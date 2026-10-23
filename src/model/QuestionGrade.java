package model;

import java.util.List;
import java.util.Optional;

/**
 * Immutable final result for one graded question.
 *
 * QuestionGrade aggregating everything that happened while
 * grading one question: the outcome {@link GradeStatus}, the marks earned, the
 * individual {@link TestCaseResult}(s), and (when the question was actually
 * run)
 * the raw {@link ProcessResult}.
 *
 * This entity is the output of grading a question.
 */
public final class QuestionGrade {

    /** Matches the {@code QuestionDefinition} id . */
    private final String questionId;

    /** Outcome kind of this grade. */
    private final GradeStatus status;

    /** Marks earned for the question (zero or positive). */
    private final double earnedMarks;

    /** Immutable list of per-case results; may be empty. */
    private final List<TestCaseResult> testCaseResults;

    /** Raw process result; absent when the attempt was skipped. */
    private final Optional<ProcessResult> processResult;

    /** Human-readable message; present for compile/runtime errors, else absent. */
    private final String message;

    /**
     * @param questionId      question id
     * @param status          outcome kind
     * @param earnedMarks     marks earned for the question
     * @param testCaseResults per-case results; null becomes an empty list and
     *                        the contents are defensively copied into an
     *                        unmodifiable list
     * @param processResult   raw process result, or {@code null} when absent
     *                        (e.g. the attempt was skipped)
     * @param message         optional human-readable message, or {@code null}
     * @throws IllegalArgumentException if {@code status} is {@code null}
     */
    public QuestionGrade(String questionId, GradeStatus status, double earnedMarks,
            List<TestCaseResult> testCaseResults,
            ProcessResult processResult, String message) {

        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        this.questionId = questionId == null ? "" : questionId;
        this.status = status;
        this.earnedMarks = earnedMarks;
        this.testCaseResults = (testCaseResults == null)
                ? List.of() // if no test case result, return empty list
                : List.copyOf(testCaseResults);
        this.processResult = Optional.ofNullable(processResult);
        this.message = message==null ? "" : message;
    }

    /** @return the question id */
    public String getQuestionId() {
        return questionId;
    }

    /** @return the outcome kind */
    public GradeStatus getStatus() {
        return status;
    }

    /** @return marks earned for the question */
    public double getEarnedMarks() {
        return earnedMarks;
    }

    /** @return the unmodifiable list of per-case results (possibly empty) */
    public List<TestCaseResult> getTestCaseResults() {
        return testCaseResults;
    }

    /** @return the raw process result, or empty when absent */
    public Optional<ProcessResult> getProcessResult() {
        return processResult;
    }

    /** @return the message, or empty when absent */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "QuestionGrade{questionId='" + questionId + '\''
                + ", status=" + status
                + ", earnedMarks=" + earnedMarks
                + ", testCases=" + testCaseResults.size()
                + ", hasProcessResult=" + processResult.isPresent()
                + ", hasMessage=" + message + '}';
    }
}
