package org.imdea.fixcheck.assertion;

import com.github.javaparser.ast.CompilationUnit;
import org.imdea.fixcheck.assertion.common.AssertionsHelper;
import org.imdea.fixcheck.prefix.Prefix;
import org.imdea.fixcheck.transform.common.TransformationHelper;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * CodeLlamaOllama class: assertion generator using a CodeLlama model through Ollama.
 *
 * @author Facundo Molina <facundo.molina@imdea.org>
 */
public class CodeLlamaOllama extends AssertionGenerator {

  private final String API_URL = "http://localhost:11434/api/generate";

  // [CHANGED] Strengthen constraints to prevent non-compilable or missing assertions
  private final String SYSTEM =
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

  public CodeLlamaOllama() {}

  @Override
  public void generateAssertions(Prefix prefix) {
    // Prepare the prompt
    String prompt = generatePrompt(prefix);
    System.out.println("prompt:");
    System.out.println(prompt);
    // Perform the call to the OpenAI API
    String responseText = performCall(prompt);
    List<String> assertionsStr = getAssertionsFromResponseText(responseText);
    System.out.println("---> assertions: " + assertionsStr);
    System.out.println();
    AssertionsHelper.appendAssertionsToPrefix(assertionsStr, prefix);
    // Update the class name
    updateClassName(prefix);
  }

  /**
   * Generate the prompt for the model.
   */
  private String generatePrompt(Prefix prefix) {
    // [ADDED] Read metadata only from environment variables (fallback to empty string if missing)
    String rootCause = System.getenv("ROOT_CAUSE");
    if (rootCause == null) rootCause = "";
    String errorLocation = System.getenv("ERROR_LOCATION");
    if (errorLocation == null) errorLocation = "";

    // [CHANGED] Include guardrails in the prompt to avoid non-compilable or missing assertions
    String prompt =
        "Complete the following Java unit test by appending ONLY assertion statements that COMPILE.\n"
      + "Constraints:\n"
      + "1) Output only assertions (no imports / declarations / comments / extra code).\n"
      + "2) Use only existing variables/methods/identifiers from the snippet.\n"
      + "3) Each line must be a valid Java statement ending with a semicolon.\n"
      + "4) If exact expected values are unclear, use the safest compilable checks (e.g., not-null, size/bounds, predicate) instead of inventing symbols.\n"
      + "5) Do not reference undefined symbols. Do not change names. Do not add control flow.\n"
      + "\n"
      + "### Root Cause:\n" + rootCause + "\n"
      + "### Error Location:\n" + errorLocation + "\n\n";

    prompt += prefix.getParent().getSourceCode() + "\n";
    prompt += prefix.getSourceCode();

    // remove the last } so that the model can complete the code
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

  /**
   * Perform the call to the OpenAI API.
   */
  private String performCall(String prompt) {
    try {
      URL url = new URL(API_URL);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json");
      JSONObject requestBody = new JSONObject();
      requestBody.put("model", "codellama");
      requestBody.put("system", SYSTEM);
      requestBody.put("prompt", prompt);
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

  /**
   * Get assertions as strings from response text
   */
  private List<String> getAssertionsFromResponseText(String text) {
    List<String> assertionsStr = new ArrayList<>();
    String[] lines = text.split("\\r?\\n"); // Split by lines
    // Process the lines of Strings backwards, until the first assertion is found
    boolean withinAssertions = false;
    for (int i = lines.length - 1; i >= 0; i--) {
      String line = lines[i];
      if (isAssertionString(line)) {
        withinAssertions = true;
        assertionsStr.add(line);
      } else if (withinAssertions) {
        break;
      }
    }
    return assertionsStr;
  }

  private boolean isAssertionString(String possibleAssertion) {
    return possibleAssertion.contains("assertEquals")
        || possibleAssertion.contains("assertNotNull")
        || possibleAssertion.contains("assertNull")
        || possibleAssertion.contains("assertTrue")
        || possibleAssertion.contains("assertFalse");
  }

  /**
   * Update the class name with a new name
   */
  private void updateClassName(Prefix prefix) {
    String currentClassName = prefix.getClassName();
    String newClassName = currentClassName + "withCodeLlama";
    prefix.setClassName(newClassName);
    CompilationUnit compilationUnit = prefix.getMethodCompilationUnit();
    compilationUnit.getClassByName(currentClassName).get().setName(newClassName);
    TransformationHelper.updateCompilationUnitNames(compilationUnit, currentClassName, newClassName);
  }
}
