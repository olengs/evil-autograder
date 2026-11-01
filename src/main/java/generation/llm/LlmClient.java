package generation.llm;

public interface LlmClient { //sends one request to a language model & returns its raw text reply
    // return the model's text completion > throw LlmException on transport or response errors
    String complete(LlmRequest request);

    // short label for logging & summaries > e.g. "gemini" or "openai-compatible"
    String providerName();
}
