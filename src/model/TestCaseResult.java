package model;

/**
 * Immutable result of a single test case produced by a tester.
 *
 * TestCaseResult records one test-case outcome and the marks earned for it
 */
public final class TestCaseResult {

    /** Identifier for the case, derived from a tester output line. */
    private final String caseId;

    /** {@code true} when the case passed. */
    private final boolean passed;

    /** Marks earned for this case (zero or positive). */
    private final double earnedMarks;

    /** Optional message from the tester. */
    private final String detail;

    /**
     * @param caseId      identifier for the case (never {@code null} after construction)
     * @param passed      whether the case passed
     * @param earnedMarks marks earned for the case
     * @param detail      optional tester message (never {@code null} after construction)
     */
    public TestCaseResult(String caseId, boolean passed, double earnedMarks, String detail) {
        this.caseId = caseId == null ? "" : caseId;
        this.passed = passed;
        this.earnedMarks = earnedMarks;
        this.detail = detail == null ? "" : detail;
    }

    /** @return the case identifier */
    public String getCaseId() {
        return caseId;
    }

    /** @return {@code true} if the case passed */
    public boolean isPassed() {
        return passed;
    }

    /** @return marks earned for this case */
    public double getEarnedMarks() {
        return earnedMarks;
    }

    /** @return the tester message, or an empty string if none */
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "TestCaseResult{caseId='" + caseId + '\''
                + ", passed=" + passed
                + ", earnedMarks=" + earnedMarks + '}';
    }
}
