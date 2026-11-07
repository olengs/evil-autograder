//stores reported outcome & earned marks for one tester case
//provides individual results that questiongrader totals & runtime verification checks

package grading;

public final class TestCaseResult {
    private final String caseId;
    private final boolean passed;
    private final double earnedMarks;
    private final String detail;
    public TestCaseResult(String caseId, boolean passed, double earnedMarks, String detail) {
        this.caseId = caseId == null ? "" : caseId;
        this.passed = passed;
        this.earnedMarks = earnedMarks;
        this.detail = detail == null ? "" : detail;
    }

    public String getCaseId() {
        return caseId;
    }

    public boolean isPassed() {
        return passed;
    }

    public double getEarnedMarks() {
        return earnedMarks;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "testcaseresult{caseId='" + caseId + '\'' + ", passed=" + passed + ", earnedMarks=" + earnedMarks + '}';
    }
}
