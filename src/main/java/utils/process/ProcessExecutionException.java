//reports failures to start a process & capture its output or finish waiting for it
//preserves the original cause for callers handling process failures

package utils.process;

public class ProcessExecutionException extends RuntimeException {
    public ProcessExecutionException(String message) {
        super(message);
    }

    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
