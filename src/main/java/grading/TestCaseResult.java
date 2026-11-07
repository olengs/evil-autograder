//stores reported outcome & earned marks for one tester case
//provides individual results that questiongrader totals & runtime verification checks

package grading;

public final class TestCaseResult {
    private final String caseId;
    private final boolean passed;
    private final double earnedMarks;

    public TestCaseResult(String caseId, boolean passed, double earnedMarks) {
        this.caseId = caseId == null ? "" : caseId;
        this.passed = passed;
        this.earnedMarks = earnedMarks;
    }

    public double getEarnedMarks() {
        return earnedMarks;
    }

    @Override
    public String toString() {
        return "testcaseresult{caseId='" + caseId + '\'' + ", passed=" + passed + ", earnedMarks=" + earnedMarks + '}';
    }
}
