package generation.llm;

import java.nio.file.Path;
import java.time.Duration;

public interface LlmClient { //sends one request to a language model & returns its raw text reply
    // generous per-request budget because model replies can be slow > shared by every provider
    // TODO to config?
    Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    // return the model's text completion > throw LlmException on transport or response errors
    String complete(LlmRequest request);

    // short label for logging & summaries > e.g. "gemini" or "openai-compatible"
    String providerName();

    // true when completeStructured can be used > the caller then asks for a typed object instead of free text
    default boolean supportsStructuredOutput() {
        return false;
    }

    // return the reply as an instance of type > the sdk builds the strict json schema from the class
    // throw LlmException when unsupported or the reply is a refusal / incomplete
    default <T> T completeStructured(LlmRequest request, Class<T> type) {
        throw new LlmException(providerName() + " does not support structured output");
    }

    // upload a pdf once so every later request can refer to it > throw LlmException when unsupported or the upload fails
    default UploadedDocument uploadPdf(Path pdf) {
        throw new LlmException(providerName() + " does not support pdf upload");
    }

    // remove an uploaded pdf > best effort so a cleanup failure never hides the real result > never called with null
    default void deleteUpload(UploadedDocument document) {
    }
}
