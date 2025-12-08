package org.imdea.fixcheck.assertion;

import com.github.javaparser.ast.CompilationUnit;
import org.imdea.fixcheck.assertion.common.AssertionsHelper;
import org.imdea.fixcheck.prefix.Prefix;
import org.imdea.fixcheck.transform.common.TransformationHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OpenAI_GPT_CONSTRAINT extends AssertionGenerator {

  private static final String API_URL = "https://api.openai.com/v1/chat/completions";
  private static final String API_KEY = System.getenv("OPENAI_API_KEY");

  private final String MODEL;
  private int maxTokens = 256;
  private double temperature = 0.2;

  public OpenAI_GPT_CONSTRAINT() {
    String envModel = System.getenv("OPENAI_MODEL");
    this.MODEL = (envModel != null && !envModel.isEmpty()) ? envModel : "gpt-4o-mini";
  }

  @Override
  public void generateAssertions(Prefix prefix) {
    String prompt = generatePrompt(prefix);
    System.out.println("prompt:");
    System.out.println(prompt);

    String responseText = performCall(prompt);
    List<String> assertionsStr = getAssertionsFromResponseText(responseText);
    System.out.println("---> assertions: " + assertionsStr);
    System.out.println();

    AssertionsHelper.appendAssertionsToPrefix(assertionsStr, prefix);
    updateClassName(prefix);
  }

  private String generatePrompt(Prefix prefix) {
    String prompt = "You are an expert programmer that helps complete Java unit tests with test assertions. " +
        "Avoid using text. Don't explain anything just complete the given code snippet with the " +
        "corresponding test assertions";
    prompt += prefix.getParent().getSourceCode() + "\n";
    prompt += prefix.getSourceCode();
    prompt = replaceLast(prompt, "}", "");
    return prompt;
  }

  private String replaceLast(String string, String substring, String replacement) {
    int index = string.lastIndexOf(substring);
    if (index == -1) return string;
    return string.substring(0, index) + replacement + string.substring(index + substring.length());
  }

  private String performCall(String prompt) {
    if (API_KEY == null || API_KEY.isEmpty()) {
      throw new RuntimeException("OPENAI_API_KEY is not set");
    }
    try {
      URL url = new URL(API_URL);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json");
      con.setRequestProperty("Authorization", "Bearer " + API_KEY);
      con.setDoOutput(true);

      JSONObject body = new JSONObject();
      body.put("model", MODEL);
      body.put("max_tokens", maxTokens);
      body.put("temperature", temperature);

      JSONArray messages = new JSONArray();

      JSONObject systemMsg = new JSONObject();
      systemMsg.put("role", "system");
      systemMsg.put("content",
          "You are an expert programmer that helps complete Java unit tests with test assertions. Don't explain anything just write the tests.\nUse only variables that are in scope at the end of the test method. Do not invent or assume any methods, classes, fields, or return values. Do not reference objects that were never initialized or may not exist.");
      messages.put(systemMsg);

      JSONObject userMsg = new JSONObject();
      userMsg.put("role", "user");
      userMsg.put("content", prompt);
      messages.put(userMsg);

      body.put("messages", messages);

      System.out.println("request: " + body);
      con.getOutputStream().write(body.toString().getBytes("UTF-8"));

      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuilder response = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      JSONObject jsonResponse = new JSONObject(response.toString());
      JSONArray choices = jsonResponse.getJSONArray("choices");
      JSONObject first = choices.getJSONObject(0);
      JSONObject message = first.getJSONObject("message");
      String content = message.getString("content");

      System.out.println("---> response: " + content);
      return content;

    } catch (Exception e) {
      System.out.println("Error while calling OpenAI Chat Completions");
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private List<String> getAssertionsFromResponseText(String text) {
    List<String> assertionsStr = new ArrayList<>();
    String[] lines = text.split("\\r?\\n");

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.startsWith("//")) continue;
      if (trimmed.startsWith("```")) continue;
      if (!trimmed.contains("assert")) continue;
      if (!trimmed.endsWith(";")) trimmed += ";";
      assertionsStr.add(trimmed);
    }
    return assertionsStr;
  }

  private void updateClassName(Prefix prefix) {
    String currentClassName = prefix.getClassName();
    String newClassName = currentClassName + "WithOpenAIGPT";
    prefix.setClassName(newClassName);
    CompilationUnit cu = prefix.getMethodCompilationUnit();
    cu.getClassByName(currentClassName).get().setName(newClassName);
    TransformationHelper.updateCompilationUnitNames(cu, currentClassName, newClassName);
  }
}
