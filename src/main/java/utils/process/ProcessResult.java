//stores a process exit code & captured output streams & timeout status
//lets grading distinguish successful execution from compilation errors & runtime errors & timeouts

package utils.process;

public final class ProcessResult {
    private final int exitCode;
    private final String standardOutput;
    private final String errorOutput;
    private final boolean timedOut;

    public ProcessResult(int exitCode, String standardOutput, String errorOutput, boolean timedOut) {
        this.exitCode = exitCode;
        this.standardOutput = standardOutput == null ? "" : standardOutput;
        this.errorOutput = errorOutput == null ? "" : errorOutput;
        this.timedOut = timedOut;
    }

    public String getStandardOutput() {
        return standardOutput;
    }

    public String getErrorOutput() {
        return errorOutput;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isSuccessful() {
        return exitCode == 0 && !timedOut;
    }

    @Override
    public String toString() {
        return "processresult{exitcode=" + exitCode + ", timedOut=" + timedOut + ", stdoutLen=" + standardOutput.length() + ", stderrLen=" + errorOutput.length() + "}";
    }
}
