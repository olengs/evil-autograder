package grading;

public final class TestCaseResult { //stores the earned marks for one tester case
    private final double earnedMarks;

    public TestCaseResult(double earnedMarks) {
        this.earnedMarks = earnedMarks;
    }

    public double getEarnedMarks() {
        return earnedMarks;
    }
}
