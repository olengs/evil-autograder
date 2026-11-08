package grading;

import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import utils.files.TemporaryDirectory;
import utils.process.JavaTools;
import utils.process.ProcessResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import submission.QuestionAttempt;
import grading.progress.ProgressListener;

public class QuestionGrader { //compiles answers, runs tester & totals completed test-case marks
    private final Duration compilationTimeout;
    private final Duration executionTimeout;

    public QuestionGrader(Duration compilationTimeout, Duration executionTimeout) {
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    public QuestionGrade grade(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester, ProgressListener progress) throws IOException {
        try (GradingWorkspace workspace = new GradingWorkspace()) {
            workspace.prepare(question, attempt, tester);
            try (TemporaryDirectory classes = new TemporaryDirectory("autograder-classes-")) {
                return gradeInWorkspace(question.getId(), tester.getMainClassName(), workspace.getDirectory(), classes.path(), progress);
            }
        }
    }

    private QuestionGrade gradeInWorkspace(String questionId, String mainClassName, Path workspaceDirectory, Path classesDirectory, ProgressListener progress) throws IOException {
        progress.compilationStarted(questionId, compilationTimeout);
        ProcessResult compilationResult = JavaTools.compile(workspaceDirectory, classesDirectory, compilationTimeout);
        if (compilationResult.isTimedOut()) {
            return failedGrade(questionId, GradeStatus.timeout, compilationResult, "compilation timed out");
        }
        if (!compilationResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.compileError, compilationResult, "compilation failed");
        }

        progress.executionStarted(questionId, executionTimeout);
        ProcessResult executionResult = JavaTools.run(workspaceDirectory, classesDirectory, mainClassName, executionTimeout);
        if (!executionResult.isTimedOut() && !executionResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.runtimeError, executionResult, "tester failed");
        }

        var parser = new TesterOutputParser();
        List<TestCaseResult> testResults;
        GradeStatus status;
        String message;

        try {
            if (executionResult.isTimedOut()) {
                //keep marks for completed cases > unfinished case has no trustworthy outcome
                testResults = parser.parseCompleted(executionResult.getStandardOutput());
                status = GradeStatus.timeout;
                message = "execution timed out > completed-test marks retained";
            } else {
                testResults = parser.parse(executionResult.getStandardOutput());
                status = GradeStatus.completed;
                message = "grading completed";
            }
        } catch (IllegalArgumentException error) {
            return failedGrade(questionId, GradeStatus.runtimeError, executionResult,
                    "invalid tester output > " + error.getMessage());
        }

        double earnedMarks = 0;
        for (TestCaseResult testCase : testResults) {
            earnedMarks += testCase.getEarnedMarks();
        }
        return new QuestionGrade(questionId, status, earnedMarks, testResults, executionResult, message);
    }

    private QuestionGrade failedGrade(String questionId, GradeStatus status, ProcessResult process, String message) {
        if (!process.getErrorOutput().isBlank()) message += "\n" + process.getErrorOutput().strip();
        return new QuestionGrade(questionId, status, 0, List.of(), process, message);
    }
}
