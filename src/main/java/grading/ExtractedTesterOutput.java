package grading;

/// stores useful lines & generated-tester validation details
public final class ExtractedTesterOutput { 
    private final String testOutput;
    private final String lastLine;
    private final boolean outcomeBeforeHeader;

    public ExtractedTesterOutput(String testOutput, String lastLine, boolean outcomeBeforeHeader) {
        this.testOutput = testOutput;
        this.lastLine = lastLine;
        this.outcomeBeforeHeader = outcomeBeforeHeader;
    }

    public String getTestOutput() {
        return testOutput;
    }

    public String getLastLine() {
        return lastLine;
    }

    public boolean hasOutcomeBeforeHeader() {
        return outcomeBeforeHeader;
    }
}
