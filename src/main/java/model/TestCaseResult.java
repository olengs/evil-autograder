package model;

public final class TestCaseResult { //stores outcome & marks of a single tester case
    private final String caseId;
    private final boolean passed;
    private final double earnedMarks;
    private final String detail;

    //@1ejun6 > store test case result > replace null id & detail with empty text
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
