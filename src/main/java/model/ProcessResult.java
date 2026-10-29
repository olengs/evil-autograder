package model;

/**
 * Immutable result of running an external process ({@code javac} or
 * {@code java}) via {@link runner.ProcessRunner}.
 *
 * ProcessResult records what the operating system reported about one
 * process run - exit code, captured standard output, captured standard
 * error, and whether the run was killed for exceeding its timeout. 
 * It carries no grading, marks, or test-case semantics.
 */
public final class ProcessResult {

    /** Process exit code. Meaningful only when {@link #timedOut} is {@code false}. */
    private final int exitcode;

    /** Everything the process wrote to standard output. */
    private final String standardOutput;

    /** Everything the process wrote to standard error. */
    private final String errorOutput;

    /** {@code true} when the process was killed for exceeding the timeout. */
    private final boolean timedOut;

    /**
     * @param exitcode       process exit code
     * @param standardOutput captured standard output (never {@code null})
     * @param errorOutput    captured standard error (never {@code null})
     * @param timedOut       whether the process was killed on timeout
     */
    public ProcessResult(int exitcode, String standardOutput, String errorOutput, boolean timedOut) {
        this.exitcode = exitcode;
        this.standardOutput = standardOutput == null ? "" : standardOutput;
        this.errorOutput = errorOutput == null ? "" : errorOutput;
        this.timedOut = timedOut;
    }

    /** @return the process exit code */
    public int getExitCode() {
        return exitcode;
    }

    /** @return captured standard output */
    public String getStandardOutput() {
        return standardOutput;
    }

    /** @return captured standard error */
    public String getErrorOutput() {
        return errorOutput;
    }

    /** @return {@code true} if the process was killed for exceeding the timeout */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * @return {@code true} when the process finished normally with exit code 0
     *         and was not timed out
     */
    public boolean isSuccessful() {
        return exitcode == 0 && !timedOut;
    }

    @Override
    public String toString() {
        return "ProcessResult{exitcode=" + exitcode
                + ", timedOut=" + timedOut
                + ", stdoutLen=" + standardOutput.length()
                + ", stderrLen=" + errorOutput.length() + "}";
    }
}
