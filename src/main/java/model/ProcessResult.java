package model;

public final class ProcessResult { //stores a processs exit code & output & timeout status
    private final int exitcode; //exit code > only meaningful if process did not time out
    private final String standardOutput; //captured standard output
    private final String errorOutput; //captured error output
    private final boolean timedOut; //true if process exceeded timeout

    //store process result > replace null output with empty text
    public ProcessResult(int exitcode, String standardOutput, String errorOutput, boolean timedOut) {
        this.exitcode = exitcode;
        this.standardOutput = standardOutput == null ? "" : standardOutput;
        this.errorOutput = errorOutput == null ? "" : errorOutput;
        this.timedOut = timedOut;
    }

    public int getExitCode() { //return exit code
        return exitcode;
    }

    public String getStandardOutput() { //return standard output
        return standardOutput;
    }

    public String getErrorOutput() { //return error output
        return errorOutput;
    }

    public boolean isTimedOut() { //check if process timed out
        return timedOut;
    }

    public boolean isSuccessful() { //successful if exit code is 0 & no timeout
        return exitcode == 0 && !timedOut;
    }

    @Override
    public String toString() {
        return "processresult{exitcode=" + exitcode + ", timedOut=" + timedOut + ", stdoutLen=" + standardOutput.length()  + ", stderrLen=" + errorOutput.length() + "}";
    }
}
