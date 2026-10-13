package autograder.model;

/**
 * Outcome kind of one question grade.
 *
 * <p>These uppercase Java constants map to the lowercase names used in the
 * project specification: {@code completed}, {@code compile_error},
 * {@code runtime_error}, {@code timeout}, {@code skipped}.</p>
 */
public enum GradeStatus {

    /** Compiled, ran, and test cases were parsed. */
    COMPLETED,

    /** Student code or the tester failed to compile. */
    COMPILE_ERROR,

    /** The tester exited abnormally at run time. */
    RUNTIME_ERROR,

    /** The tester exceeded the execution time limit. */
    TIMEOUT,

    /** The attempt was not ready (missing files or invalid). */
    SKIPPED,

    /** Placeholder used when proccessing status. */
    PENDING
}
