package grading;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import grading.progress.ProgressListener;
import submission.QuestionAttempt;
import utils.files.FileUtils;
import utils.files.TemporaryDirectory;
import utils.process.JavaTools;
import utils.process.ProcessExecutor;
import utils.process.ProcessResult;

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
        ProcessResult compilationResult = compile(workspaceDirectory, classesDirectory);
        if (compilationResult.isTimedOut()) {
            return failedGrade(questionId, GradeStatus.timeout, compilationResult, "compilation timed out");
        }
        if (!compilationResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.compileError, compilationResult, "compilation failed");
        }

        progress.executionStarted(questionId, executionTimeout);
        ProcessResult executionResult = executeTester(workspaceDirectory, classesDirectory, mainClassName);
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

    private ProcessResult compile(Path workspaceDirectory, Path classesDirectory) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(JavaTools.getCompilerPath().toString());
        command.add("-d");
        command.add(classesDirectory.toString());
        command.add("-cp");
        command.add(workspaceDirectory.toString());

        List<Path> sources;
        //workspace's direct java files are compilation unit > nested data stays as resources
        try (var files = FileUtils.streamRegularFiles(workspaceDirectory)) {
            sources = files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IOException("No Java sources in " + workspaceDirectory);
        }
        for (Path source : sources) {
            command.add(source.toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, compilationTimeout, "javac could not be started");
    }

    private ProcessResult executeTester(Path workspaceDirectory, Path classesDirectory, String mainClassName) {
        //include compiled classes & workspace resources > use current os's classpath separator
        String classpath = classesDirectory + java.io.File.pathSeparator + workspaceDirectory;
        ProcessBuilder builder = new ProcessBuilder(JavaTools.getRuntimePath().toString(), "-cp", classpath, mainClassName);
        builder.directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, executionTimeout, "java execution failed");
    }

    private QuestionGrade failedGrade(String questionId, GradeStatus status, ProcessResult process, String message) {
        if (!process.getErrorOutput().isBlank()) message += "\n" + process.getErrorOutput().strip();
        return new QuestionGrade(questionId, status, 0, List.of(), process, message);
    }
}
