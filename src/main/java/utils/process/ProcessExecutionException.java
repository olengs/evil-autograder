package utils.process;

/// reports process startup, output capture & termination failures
public class ProcessExecutionException extends RuntimeException { 
    public ProcessExecutionException(String message) {
        super(message);
    }

    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
