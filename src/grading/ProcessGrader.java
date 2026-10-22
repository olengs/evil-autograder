package grading;

import java.util.ArrayList;
import java.util.List;
import model.GradeStatus;
import model.ProcessResult;
import model.QuestionGrade;
import model.TestCaseResult;

public class ProcessGrader {

    /**
     * Process the {@code ProcessResult} to give the attempt a grade. Provide a
     * valid grade for:
     * 
     * <li>skipped/invalid attempt</li>
     * <li>failed attempt that has {@code WorkspacePreparationException} or {@code ProcessExecutionException}</li>
     * <li>successful attemp by reading its standard output </li>
     * 
     * @param questionId      : Question attempted
     * @param questionResults : process result of the execution, contain standard
     *                        output of the exesution
     * @return {@code QuestionGrade} of the attemp
     */
    public QuestionGrade gradeQuestion(String questionId, ProcessResult questionResults) {
        // question did not went through process at all
        if (questionResults == null) {
            return new QuestionGrade(questionId, GradeStatus.SKIPPED, 0, null, questionResults, "Question is skipped");
        }

        // any error in compile or execution && is not timeout
        if (!questionResults.isSuccessful() && !questionResults.isTimedOut()) {
            GradeStatus status = GradeStatus.PENDING;
            String message = "";
            // when `javac` cannot run
            if (questionResults.getErrorOutput().contains("prc-001")) {
                status = GradeStatus.COMPILE_ERROR;
                message = "Compilation failed.";
            }
            // error during `java` 
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
    }

    /**
     * Read the standard output captured from tester file execution, extract the
     * each test case id, if they {@code passed} or {@code failed} and marks award
     * for the test case. Assume each test case worth 1 mark.
     * 
     * @param questionOutput Standard output captured from tester file execution
     * @return a list of {@code TestCaseResult}
     */
    private List<TestCaseResult> readProcessOutput(String questionOutput) {
        List<TestCaseResult> testCaseResults = new ArrayList<>();

        String[] testOutputs = questionOutput.split("Test ");
        for (String testOutput : testOutputs) {
            if (!testOutput.contains(":")) {
                continue;
            }
            // TODO (iter2): check if question needs a pass attribute
            String testId = testOutput.substring(0, testOutput.indexOf(":"));
            Integer passedIndex = testOutput.indexOf("Passed");
            double earnedMarks = passedIndex >= 0 ? 1 : 0;
            boolean passed = passedIndex >= 0;
            // TODO (iter2): do we need message?
            TestCaseResult testCase = new TestCaseResult(testId, passed, earnedMarks, "completed");

            testCaseResults.add(testCase);
        }
        return testCaseResults;
    }
}
