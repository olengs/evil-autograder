package generation.llm;

import java.nio.file.Path;

public interface LlmClient { //sends one request to a language model & returns structured output
    String providerName();

    //return reply as an instance of type > sdk builds strict json schema from class
    //throw LlmException on request errors or when reply is a refusal / incomplete
    <T> T completeStructured(LlmRequest request, Class<T> type);

    //upload optional exam pdf once for combined generation request > fail when unsupported or upload fails
    default String uploadPdf(Path pdf) {
        throw new LlmException(providerName() + " does not support pdf upload");
    }

    //remove uploaded exam pdf after request > best effort so cleanup never hides result > never called with null
    default void deleteUpload(String document) {
    }
}
