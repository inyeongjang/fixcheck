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

public class Llama_Ollama_OUTPUT extends AssertionGenerator {

  private final String API_URL = "http://localhost:11434/api/generate";

  private final String SYSTEM =
      "You are an expert programmer that helps complete Java unit tests with test assertions. "
    + "Output ONLY valid Java JUnit assertion statements ending with a semicolon. "
    + "No comments, explanations, imports, or extra text. "
    + "Examples:assertEquals(expected, actual); assertTrue(condition); assertFalse(condition); assertNotNull(value); assertNull(value);";

  public Llama_Ollama_OUTPUT() {}

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
    String prompt =
        "You are an expert programmer that helps complete Java unit tests with test assertions. "
      + "Avoid using text. Don't explain anything just complete the given code snippet with the "
      + "corresponding test assertions";
    prompt += prefix.getParent().getSourceCode() + "\n";
    prompt += prefix.getSourceCode();
    prompt = replaceLast(prompt, "}", "");
    return prompt;
  }

  private String replaceLast(String string, String substring, String replacement) {
    int index = string.lastIndexOf(substring);
    if (index == -1)
      return string;
    return string.substring(0, index) + replacement
        + string.substring(index + substring.length());
  }

  private String performCall(String prompt) {
    try {
      URL url = new URL(API_URL);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json");

      JSONObject requestBody = new JSONObject();
      requestBody.put("model", "llama3.2:3b");
      requestBody.put("system", SYSTEM);
      requestBody.put("prompt", prompt);
      requestBody.put("options", new JSONObject().put("stop", new JSONArray().put("}")));
      requestBody.put("stream", false);
      System.out.println("request: " + requestBody);

      con.setDoOutput(true);
      con.getOutputStream().write(requestBody.toString().getBytes("UTF-8"));

      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuffer response = new StringBuffer();
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      JSONObject jsonResponse = new JSONObject(response.toString());
      String completion = jsonResponse.getString("response");
      System.out.println("---> response: " + completion);
      return completion;

    } catch (Exception e) {
      System.out.println("Error while performing the call to the model llama3.1 through Ollama");
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private List<String> getAssertionsFromResponseText(String text) {
    List<String> assertionsStr = new ArrayList<>();
    String[] lines = text.split("\\r?\\n");

    for (String line : lines) {
      String t = line.trim();
      if (t.isEmpty()) continue;
      if (t.startsWith("//")) continue;
      if (t.startsWith("```")) continue;
      if (!t.contains("assert")) continue;
      if (!t.endsWith(";")) t += ";";
      assertionsStr.add(t);
    }
    return assertionsStr;
  }

  private void updateClassName(Prefix prefix) {
    String currentClassName = prefix.getClassName();
    String newClassName = currentClassName + "withLlama";
    prefix.setClassName(newClassName);
    CompilationUnit compilationUnit = prefix.getMethodCompilationUnit();
    compilationUnit.getClassByName(currentClassName).get().setName(newClassName);
    TransformationHelper.updateCompilationUnitNames(compilationUnit, currentClassName, newClassName);
  }
}
