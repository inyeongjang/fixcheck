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

public class Llama_Ollama_New extends AssertionGenerator {

  private static final String API_URL = "http://localhost:11434/api/generate";

private static final String SYSTEM =
    "You are an expert Java unit-test assistant. "
  + "First reason step-by-step internally to choose correct assertions, but NEVER reveal reasoning. "
  + "Output ONLY compilable Java assertion statements (e.g., assertEquals(...); assertTrue(...); assertFalse(...); "
  + "assertNotNull(...); assertNull(...);) each ending with a semicolon. "
  + "Do NOT add imports, variables, methods, classes, try/catch, comments, or code fences. "
  + "Do NOT modify existing identifiers. "
  + "Use only identifiers that already exist in the provided test snippet. "
  + "Prefer precise assertions over generic ones, but if value is unknown, choose the safest compilable assertion "
  + "(e.g., null/size/bounds checks) to avoid non-compilation. "
  + "Do not reference undefined symbols. "
  + "Do not omit assertions if any can be safely added.";

  public Llama_Ollama_New() {}

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
    String rootCause = System.getenv("ROOT_CAUSE");
    if (rootCause == null) rootCause = "";
    String errorLocation = System.getenv("ERROR_LOCATION");
    if (errorLocation == null) errorLocation = "";

    StringBuilder sb = new StringBuilder();
    sb.append("Complete the following Java unit test by appending ONLY assertion statements that COMPILE.\n")
      .append("Constraints:\n")
      .append("1) Output only assertions (no imports/declarations/comments/extra code).\n")
      .append("2) Use only existing variables/methods/identifiers from the snippet.\n")
      .append("3) Each line must be a valid Java statement ending with a semicolon.\n")
      .append("4) If exact expected values are unclear, use the safest compilable checks (e.g., not-null, size/bounds, predicate) instead of inventing symbols.\n")
      .append("5) Do not reference undefined symbols. Do not change names. Do not add control flow.\n\n")
      .append("### Root Cause:\n")
      .append(rootCause)
      .append("\n\n### Error Location:\n")
      .append(errorLocation)
      .append("\n\n")
      .append(prefix.getParent().getSourceCode())
      .append("\n")
      .append(prefix.getSourceCode());

    String prompt = sb.toString();
    return replaceLast(prompt, "}", "");
  }

  private String replaceLast(String string, String substring, String replacement) {
    int index = string.lastIndexOf(substring);
    if (index == -1) return string;
    return string.substring(0, index) + replacement + string.substring(index + substring.length());
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
      System.out.println("Error while performing the call to the model llama3.2 through Ollama");
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
    String newClassName = currentClassName + "withLlama_Context";
    prefix.setClassName(newClassName);
    CompilationUnit cu = prefix.getMethodCompilationUnit();
    cu.getClassByName(currentClassName).get().setName(newClassName);
    TransformationHelper.updateCompilationUnitNames(cu, currentClassName, newClassName);
  }
}
