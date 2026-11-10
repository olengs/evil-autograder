package utils.process;

public final class ProcessResult { //stores process output, exit code & termination status
    private final int exitCode;
    private final String standardOutput;
    private final String errorOutput;
    private final boolean timedOut;
    private final boolean standardOutputLimitExceeded;
    private final boolean errorOutputLimitExceeded;

    public ProcessResult(int exitCode, String standardOutput, String errorOutput, boolean timedOut) {
        this(exitCode, standardOutput, errorOutput, timedOut, false, false);
    }

    public ProcessResult(int exitCode, String standardOutput, String errorOutput, boolean timedOut, boolean standardOutputLimitExceeded, boolean errorOutputLimitExceeded) {
        this.exitCode = exitCode;
        this.standardOutput = standardOutput == null ? "" : standardOutput;
        this.errorOutput = errorOutput == null ? "" : errorOutput;
        this.timedOut = timedOut;
        this.standardOutputLimitExceeded = standardOutputLimitExceeded;
        this.errorOutputLimitExceeded = errorOutputLimitExceeded;
    }

    public String getStandardOutput() {
        return standardOutput;
    }

    public String getErrorOutput() {
        return errorOutput;
    }

    public boolean isStandardOutputLimitExceeded() {
        return standardOutputLimitExceeded;
    }

    public boolean isErrorOutputLimitExceeded() {
        return errorOutputLimitExceeded;
    }

    public boolean isOutputLimitExceeded() {
        return standardOutputLimitExceeded || errorOutputLimitExceeded;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isSuccessful() {
        return exitCode == 0 && !timedOut && !isOutputLimitExceeded();
    }

    @Override
    public String toString() {
        return "processresult{exitCode=" + exitCode + ", timedOut=" + timedOut + ", standardOutputLimitExceeded=" + standardOutputLimitExceeded + ", errorOutputLimitExceeded=" + errorOutputLimitExceeded + "}";
    }
}
