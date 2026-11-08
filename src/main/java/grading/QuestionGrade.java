package grading;

import utils.process.ProcessResult;
import java.util.List;
import java.util.Optional;

public final class QuestionGrade { //stores a question's marks, status, test cases & process result
    private final String questionId;
    private final GradeStatus status;
    private final double earnedMarks;
    private final List<TestCaseResult> testCaseResults;
    private final Optional<ProcessResult> processResult;
    private final String message;

    public QuestionGrade(String questionId, GradeStatus status, double earnedMarks, List<TestCaseResult> testCaseResults, ProcessResult processResult, String message) {
        if (status == null) throw new IllegalArgumentException("status is required");

        this.questionId = questionId == null ? "" : questionId;
        this.status = status;
        this.earnedMarks = earnedMarks;
        this.testCaseResults = testCaseResults == null ? List.of() : List.copyOf(testCaseResults);
        this.processResult = Optional.ofNullable(processResult);
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

    public Optional<ProcessResult> getProcessResult() {
        return processResult;
    }

    public String getMessage() {
        return message;
    }
}
