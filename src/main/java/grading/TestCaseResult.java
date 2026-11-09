package grading;

public final class TestCaseResult { //stores one tester case's ID, outcome & earned marks
    private final String caseId;
    private final boolean passed;
    private final double earnedMarks;

    public TestCaseResult(String caseId, boolean passed, double earnedMarks) {
        this.caseId = caseId == null ? "" : caseId;
        this.passed = passed;
        this.earnedMarks = earnedMarks;
    }

    public TestCaseResult scaled(double factor) {
        return new TestCaseResult(caseId, passed, earnedMarks * factor);
    }

    public double getEarnedMarks() {
        return earnedMarks;
    }

    @Override
    public String toString() {
        return "testcaseresult{caseId='" + caseId + '\'' + ", passed=" + passed + ", earnedMarks=" + earnedMarks + '}';
    }
}
