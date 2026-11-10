package grading;

import java.util.List;


public final class QuestionGrade { //stores a question's marks, status, test cases
    private final String questionId;
    private final GradeStatus status;
    private final double earnedMarks;
    private final List<TestCaseResult> testCaseResults;
    private final String message;

    public QuestionGrade(String questionId, GradeStatus status, double earnedMarks, List<TestCaseResult> testCaseResults, String message) {
        if (status == null) throw new IllegalArgumentException("status is required");

        this.questionId = questionId == null ? "" : questionId;
        this.status = status;
        this.earnedMarks = earnedMarks;
        this.testCaseResults = testCaseResults == null ? List.of() : List.copyOf(testCaseResults);
        this.message = message == null ? "" : message;
    }

    public static double totalMarks(List<QuestionGrade> grades) {
        double total = 0;
        for (QuestionGrade grade : grades) total += grade.getEarnedMarks();
        return total;
    }

    public String getQuestionId() {
        return questionId;
    }

    public GradeStatus getStatus() {
        return status;
    }

    public double getEarnedMarks() {
        return earnedMarks;
    }

    public List<TestCaseResult> getTestCaseResults() {
        return testCaseResults;
    }


    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "QuestionGrade{questionId='" + questionId + '\'' + ", status=" + status + ", earnedMarks=" + earnedMarks + ", testCases=" + testCaseResults.size() + ", hasMessage=" + message + '}';
    }
}
