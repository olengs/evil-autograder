package generation.llm;

public class LlmException extends RuntimeException { //reports a problem calling the model or reading its reply
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
