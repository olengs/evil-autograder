package autograder.autograderexception;

/**
 * Runtime exception raised while executing an external process
 * ({@code javac} or {@code java}) during grading.
 *
 * Each failure path carries an error code so callers and logs know what
 * failed:
 *
 * <ul>
 *   <li>prc-001: {@code javac} executable could not be started</li>
 *   <li>prc-002: {@code java} executable could not be started</li>
 *   <li>prc-003: stream capture failed</li>
 *   <li>prc-004: worker interrupted while waiting</li>
 * </ul>
 *
 */
public class ProcessExecutionException extends RuntimeException {

    /** Error code (e.g. {@code prc-001}); may be {@code null}. */
    private final String code;

    /**
     * Constructs a new exception with a detailed message and a {@code null} code.
     *
     * @param message human-readable description of the failure
     */
    public ProcessExecutionException(String message) {
        super(message);
        this.code = null;
    }

    /**
     * Constructs a new exception with both message and cause.
     *
     * @param message human-readable description of the failure
     * @param cause   underlying cause of the failure
     */
    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    /**
     * Code-carrying overload.
     *
     * @param code    error code (prc-00x)
     * @param message human-readable description of the failure
     */
    public ProcessExecutionException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Code-carrying overload with cause.
     *
     * @param code    error code (prc-00x)
     * @param message human-readable description of the failure
     * @param cause   underlying cause of the failure
     */
    public ProcessExecutionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * @return the error code (prc-00x), or {@code null} if none was supplied
     */
    public String getCode() {
        return code;
    }
}
