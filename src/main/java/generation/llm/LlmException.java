package generation.llm;

public class LlmException extends RuntimeException { //reports a problem calling the model or reading its reply
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    // an sdk call failed > keep the provider's own message & the cause
    public static LlmException requestFailed(String provider, RuntimeException cause) {
        return new LlmException(provider + " request failed > " + cause.getMessage(), cause);
    }
}
