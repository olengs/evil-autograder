package model;

import java.util.List;
import java.util.Optional;

public final class QuestionGrade { //stores final result for one question > status & marks & test cases & process result
    private final String questionId;
    private final GradeStatus status;
    private final double earnedMarks;
    private final List<TestCaseResult> testCaseResults; //test case results > list cannot be changed
    private final Optional<ProcessResult> processResult; //process result > empty if not supplied
    private final String message; //grading message > empty if not supplied

    //require status & store results > copy test cases & handle null values
    public QuestionGrade(String questionId, GradeStatus status, double earnedMarks,
            List<TestCaseResult> testCaseResults,
            ProcessResult processResult, String message) {

        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        this.questionId = questionId == null ? "" : questionId;
        this.status = status;
        this.earnedMarks = earnedMarks;
        this.testCaseResults = (testCaseResults == null)? List.of() : List.copyOf(testCaseResults); //no test case results > use empty list
        this.processResult = Optional.ofNullable(processResult);
        this.message = message==null ? "" : message;
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

    //return test case results
    public List<TestCaseResult> getTestCaseResults() {
        return testCaseResults;
    }

    //return process result if present
    public Optional<ProcessResult> getProcessResult() {
        return processResult;
    }

    //return message
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "QuestionGrade{questionId='" + questionId + '\'' + ", status=" + status + ", earnedMarks=" + earnedMarks  + ", testCases=" + testCaseResults.size() + ", hasProcessResult=" + processResult.isPresent() + ", hasMessage=" + message + '}';
    }
}
