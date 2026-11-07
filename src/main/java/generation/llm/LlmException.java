package generation.llm;

public class LlmException extends RuntimeException { //reports a problem calling model or reading its reply
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    //an sdk call failed > keep providers own message & cause
    public static LlmException requestFailed(String provider, RuntimeException cause) {
        return new LlmException(provider + " request failed > " + cause.getMessage(), cause);
    }
}
