//stores one question's grading status & earned marks & test cases & process details
//carries grading outcome into console reporting & runtime verification & scoresheet totals

package grading;

import utils.process.ProcessResult;
import java.util.List;
import java.util.Optional;

public final class QuestionGrade {
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

    @Override
    public String toString() {
        return "QuestionGrade{questionId='" + questionId + '\'' + ", status=" + status + ", earnedMarks=" + earnedMarks + ", testCases=" + testCaseResults.size() + ", hasProcessResult=" + processResult.isPresent() + ", hasMessage=" + message + '}';
    }
}
