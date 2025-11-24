package org.imdea.fixcheck.properties;

import java.util.HashMap;
import java.util.Map;

/**
 * AssertionGeneratorOptions class: handle the options for the assertion generator.
 * @author Facundo Molina
 */
public class AssertionGeneratorProperty {

  // Declare and initialize the static map
  private static final Map<String, String> Options = createMap();

  // Method to initialize the map with values
  private static Map<String, String> createMap() {
    Map<String, String> map = new HashMap<>();
    map.put("assert-true", "org.imdea.fixcheck.assertion.AssertTrueGenerator");
    map.put("previous-assertion", "org.imdea.fixcheck.assertion.UsePreviousAssertGenerator");
    map.put("replit-code-llm", "org.imdea.fixcheck.assertion.ReplitCodeLLM");
    map.put("gpt-3.5", "org.imdea.fixcheck.assertion.GPT3_5Turbo");
    
    map.put("codellama7b", "org.imdea.fixcheck.assertion.CodeLlama_7B");
    map.put("codellama7b-new", "org.imdea.fixcheck.assertion.CodeLlama_7B_New");
    
    map.put("codellama13b", "org.imdea.fixcheck.assertion.CodeLlama_13B");
    map.put("codellama13b-new", "org.imdea.fixcheck.assertion.CodeLlama_13B_New");
    
    map.put("llama", "org.imdea.fixcheck.assertion.Llama_Ollama");
    map.put("llama-new", "org.imdea.fixcheck.assertion.Llama_Ollama_New");
    
    map.put("gpt", "org.imdea.fixcheck.assertion.OpenAI_GPT");
    map.put("gpt-new", "org.imdea.fixcheck.assertion.OpenAI_GPT_New");
    
    return map;
  }

  public static String parseOption(String option) {
    if (!Options.containsKey(option)) {
      System.out.println("Error: Invalid option for assertion generator: " + option);
      System.out.println("Valid options are: " + Options.keySet());
      System.exit(1);
    }
    return Options.get(option);
  }
}
