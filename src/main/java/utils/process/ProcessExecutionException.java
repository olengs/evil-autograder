package utils.process;

public class ProcessExecutionException extends RuntimeException { //reports process startup, output capture & termination failures
    public ProcessExecutionException(String message) {
        super(message);
    }

    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
