package grading;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import model.GradeStatus;
import model.ProcessResult;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.QuestionGrade;
import model.TestCaseResult;
import model.TesterDefinition;
import utils.JavaProcessUtils;

public class QuestionGrader { //compiles & runs tester & calculates marks for one question
    //separate time limits for compiling & running
    private final Duration compilationTimeout;
    private final Duration executionTimeout;

    public QuestionGrader(Duration compilationTimeout, Duration executionTimeout) {
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    public QuestionGrade grade(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester) throws IOException {
        //prepare temporary files > workspace closes & cleans up even if grading fails
        try (GradingWorkspace workspace = new GradingWorkspace()) {
            workspace.prepare(question, attempt, tester);
            return gradeInWorkspace(question.getId(), tester.getMainClassName(), workspace.getDirectory());
        }
    }

    private QuestionGrade gradeInWorkspace(String questionId, String mainClassName, Path workspaceDirectory) throws IOException {
        //compile first > stop if compilation times out or fails
        ProcessResult compilationResult = JavaProcessUtils.compile(workspaceDirectory, compilationTimeout);

        if (compilationResult.isTimedOut()) {
            return failedGrade(questionId, GradeStatus.TIMEOUT, compilationResult, "compilation timed out");
        }
        if (!compilationResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.COMPILE_ERROR, compilationResult, "compilation failed");
        }

        //run tester only after successful compilation
        ProcessResult executionResult = JavaProcessUtils.run(workspaceDirectory, mainClassName, executionTimeout);
        if (!executionResult.isTimedOut() && !executionResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.RUNTIME_ERROR, executionResult, "tester failed");
        }

        //parse tester output & add marks from each test case
        //@1ejun6 > keep completed test cases on timeout > ignore unfinished cases
        TesterOutputParser parser = new TesterOutputParser();
        List<TestCaseResult> testResults = executionResult.isTimedOut() ? parser.parseCompleted(executionResult.getStandardOutput()) : parser.parse(executionResult.getStandardOutput());
        double marks = 0;
        for (TestCaseResult testCase : testResults) {
            marks += testCase.getEarnedMarks();
        }
        return new QuestionGrade(questionId, executionResult.isTimedOut() ? GradeStatus.TIMEOUT : GradeStatus.COMPLETED,
                marks, testResults, executionResult,
                executionResult.isTimedOut() ? "execution timed out > completed-test marks retained" : "grading completed");
    }

    private QuestionGrade failedGrade(String questionId, GradeStatus status,
            ProcessResult process, String message) {
        //failed grade has 0 marks & no test case results > keep process details
        if (!process.getErrorOutput().isBlank()) message += "\n" + process.getErrorOutput().strip();
        return new QuestionGrade(questionId, status, 0, List.of(), process, message);
    }

}
