package autograder.grading;

import autograder.model.GradeStatus;
import autograder.model.ProcessResult;
import autograder.model.QuestionGrade;
import autograder.model.TestCaseResult;
import java.util.ArrayList;
import java.util.List;

public class ProcessGrade {
    // private static final Pattern delimiter = Pattern.compile("[-+]");

    public QuestionGrade gradeQuestion(String questionId, ProcessResult questionResults) {
        // question did not went through process at all
        if (questionResults == null) {
            return new QuestionGrade(questionId, GradeStatus.SKIPPED, 0, null, questionResults, "Question is skipped");
        }

        // any error in compile or execution && is not timeout
        if (!questionResults.isSuccessful() && !questionResults.isTimedOut()) {
            GradeStatus status = GradeStatus.PENDING;
            String message = "";
            // when javac cannot run
            if (questionResults.getErrorOutput().contains("prc-001")) {
                status = GradeStatus.COMPILE_ERROR;
                message = "Compilation failed.";
            }
            // the rest of the execution error & java
            else if (questionResults.getErrorOutput().contains("prc")) {
                status = GradeStatus.RUNTIME_ERROR;
                message = "Execution of tester file failed.";

            }
            return new QuestionGrade(questionId, status, 0, null, questionResults, message);
        }

        // grading bengin for successful executed and timeout tester
        List<TestCaseResult> testCaseResults = readProcessOutput(questionResults.getStandardOutput());
        double earnedMarks = testCaseResults.stream().mapToDouble(TestCaseResult::getEarnedMarks).sum();
        GradeStatus status = questionResults.isTimedOut() ? GradeStatus.TIMEOUT : GradeStatus.COMPLETED;

        return new QuestionGrade(questionId, status, earnedMarks, testCaseResults, questionResults,
                "Grading Successful");

        // String questionOutput = questionResults.getStandardOutput();
        // List<TestCaseResult> testCaseResults = new ArrayList<>();
        // double totalMarks = 0;

        // String[] testOutputs = questionOutput.split("Test ");
        // for (String testOutput : testOutputs) {
        // // TODO: check if question needs a pass attribute
        // String testId = testOutput.substring(0, testOutput.indexOf(":"));
        // Integer passedIndex = testOutput.indexOf("Passed");
        // double earnedMarks = passedIndex >= 0 ? 1 : 0;
        // boolean passed = passedIndex >= 0;
        // // TODO: do we need message?
        // TestCaseResult testCase = new TestCaseResult(testId, passed, earnedMarks,
        // "completed");

        // testCaseResults.add(testCase);
        // totalMarks += earnedMarks;
        // }

        // if (questionResults.isTimedOut()) {
        // return new QuestionGrade(questionId, GradeStatus.TIMEOUT, totalMarks,
        // testCaseResults, questionResults,
        // "Question got timed out");
        // } else {
        // return new QuestionGrade(questionId, GradeStatus.COMPLETED, totalMarks,
        // testCaseResults, questionResults,
        // "Grading Successful");

        // }
        // return null;
    }

    public List<TestCaseResult> readProcessOutput(String questionOutput) {
        List<TestCaseResult> testCaseResults = new ArrayList<>();

        String[] testOutputs = questionOutput.split("Test ");
        for (String testOutput : testOutputs) {
            // TODO: check if question needs a pass attribute
            String testId = testOutput.substring(0, testOutput.indexOf(":"));
            Integer passedIndex = testOutput.indexOf("Passed");
            double earnedMarks = passedIndex >= 0 ? 1 : 0;
            boolean passed = passedIndex >= 0;
            // TODO: do we need message?
            TestCaseResult testCase = new TestCaseResult(testId, passed, earnedMarks, "completed");

            testCaseResults.add(testCase);
        }
        return testCaseResults;
    }
}
