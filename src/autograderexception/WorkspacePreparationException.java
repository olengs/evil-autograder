package autograderexception;

/**
 * Checked exception raised while preparing an isolated grading workspace.
 *
 * Each failure path in the workspace-preparation stage is associated with an
 * error code (wsp-001 .. wsp-005) so that callers and logs know what failed.
 *
 *   <li>wsp-001: temp workspace cannot be created</li>
 *   <li>wsp-002: answer file copy failed</li>
 *   <li>wsp-003: question resource folder missing</li>
 *   <li>wsp-004: tester source copy failed</li>
 *   <li>wsp-005: workspace cleanup failed</li>
 */
public class WorkspacePreparationException extends RuntimeException {

    /** Error code (e.g. {@code wsp-001}); may be {@code null}. */
    private final String code;

    /**
     * Contruct new exception with detailed message and code as {@code null}.
     *
     * @param message
     */
    public WorkspacePreparationException(String message) {
        super(message);
        this.code = null;
    }

    /**
     * Contruct new exception with both message and cause.
     *
     * @param message human-readable description of the failure
     * @param cause   underlying cause of the failure
     */
    public WorkspacePreparationException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    /**
     * Code-carrying overload.
     *
     * @param code    error code (wsp-00x)
     * @param message human-readable description of the failure
     */
    public WorkspacePreparationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Code-carrying overload with cause.
     *
     * @param code    error code (wsp-00x)
     * @param message human-readable description of the failure
     * @param cause   underlying cause of the failure
     */
    public WorkspacePreparationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * @return the error code (wsp-00x), or {@code null} if none was supplied
     */
    public String getCode() {
        return code;
    }
}
