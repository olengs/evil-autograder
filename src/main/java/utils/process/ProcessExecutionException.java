//reports failures to start a process & capture its output or finish waiting for it
//preserves original cause & optional error code for callers handling process failures

package utils.process;

public class ProcessExecutionException extends RuntimeException {
    private final String code;

    public ProcessExecutionException(String message) {
        super(message);
        this.code = null;
    }

    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public ProcessExecutionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
