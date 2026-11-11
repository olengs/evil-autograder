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
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;
import utils.process.JavaTools;
import utils.process.ProcessExecutor;
import utils.process.ProcessResult;

/// compiles answers, runs tester & totals completed test-case marks
public class QuestionGrader { 
    private final long outputMaxBytes;
    private final Duration compilationTimeout;
    private final Duration executionTimeout;

    public QuestionGrader(Duration compilationTimeout, Duration executionTimeout) {
        this(compilationTimeout, executionTimeout, utils.process.ProcessExecutor.DEFAULT_OUTPUT_MAX_BYTES);
    }
    public QuestionGrader(Duration compilationTimeout, Duration executionTimeout, long outputMaxBytes) {
        this.outputMaxBytes = outputMaxBytes;
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    public QuestionGrade grade(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester, ProgressListener progress, String studentLabel) throws IOException {
        try (GradingWorkspace workspace = new GradingWorkspace(attempt.getQuestionDirectory())) {
            workspace.prepare(question, attempt, tester);
            Path classes = workspace.createClassesDirectory();
            return gradeInWorkspace(question.getId(), tester, workspace.getDirectory(), classes, workspace.getCompilationSources(), progress, studentLabel);
        }
    }

    private QuestionGrade gradeInWorkspace(String questionId, TesterDefinition tester, Path workspaceDirectory, Path classesDirectory, List<Path> compilationSources, ProgressListener progress, String studentLabel) throws IOException {
        progress.compilationStarted(questionId, compilationTimeout);
        ProcessResult compilationResult = compile(workspaceDirectory, classesDirectory, compilationSources);
        logDiagnostics(studentLabel, questionId, "compilation", compilationResult);
        if (compilationResult.isOutputLimitExceeded())
            return failedGrade(questionId, GradeStatus.compileError, outputLimitMessage("compilation", compilationResult));
        if (compilationResult.isTimedOut()) {
            return failedGrade(questionId, GradeStatus.timeout, "compilation timed out after " + compilationTimeout.toMillis() / 1000.0 + "s");
        }
        if (!compilationResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.compileError, "compilation failed");
        }

        progress.executionStarted(questionId, executionTimeout);
        ProcessResult executionResult = executeTester(workspaceDirectory, classesDirectory, tester.getMainClassName());
        logDiagnostics(studentLabel, questionId, "execution", executionResult);
        if (executionResult.isOutputLimitExceeded())
            return failedGrade(questionId, GradeStatus.runtimeError, outputLimitMessage("execution", executionResult));
        if (!executionResult.isTimedOut() && !executionResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.runtimeError, "tester execution failed; see diagnostic log");
        }

        List<TestCaseResult> testResults;
        GradeStatus status;
        String message;

        try {
            var extracted = new TesterOutputExtractor().extract(executionResult.getStandardOutput());
            var parser = new TesterOutputParser();
            if (executionResult.isTimedOut()) {
                //keep marks for completed cases > unfinished case has no trustworthy outcome
                testResults = parser.parseCompleted(extracted.getTestOutput());
                status = GradeStatus.timeout;
                message = "execution timed out after " + executionTimeout.toMillis() / 1000.0 + "s > completed-test marks retained";
            } else {
                testResults = parser.parse(extracted.getTestOutput());
                status = GradeStatus.completed;
                message = "grading completed";
            }
        } catch (IllegalArgumentException error) {
            return failedGrade(questionId, GradeStatus.runtimeError,
                    "invalid tester output > " + error.getMessage());
        }

        int totalCases = tester.getNumTestCases();
        if (totalCases > 0 && (testResults.size() > totalCases
                || (status == GradeStatus.completed && testResults.size() != totalCases))) {
            return failedGrade(questionId, GradeStatus.runtimeError,
                    "tester output does not match declared numTestCases > " + totalCases);
        }
        if (totalCases == 0 && status == GradeStatus.completed) totalCases = testResults.size();
        if (totalCases == 0) message = "execution timed out > expected total unknown; partial marks unavailable";
        double weight = totalCases == 0 ? 0 : tester.getMaximumMarks() / totalCases;
        testResults = testResults.stream().map(result -> result.scaled(weight)).toList();
        double earnedMarks = testResults.stream().mapToDouble(TestCaseResult::getEarnedMarks).sum();
        return new QuestionGrade(questionId, status, earnedMarks, testResults, message);
    }

    private ProcessResult compile(Path workspaceDirectory, Path classesDirectory, List<Path> compilationSources) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(JavaTools.getCompilerPath().toString());
        command.add("-d");
        command.add(classesDirectory.toString());
        command.add("-cp");
        command.add(workspaceDirectory.toString());

        //compile only this question's files > sibling answers sharing the folder stay as resources, not compile units
        List<Path> sources = compilationSources.stream().sorted().toList();
        if (sources.isEmpty()) {
            throw new IOException("No Java sources for question in " + workspaceDirectory);
        }
        for (Path source : sources) {
            command.add(source.toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, compilationTimeout, "javac could not be started", outputMaxBytes);
    }

    private ProcessResult executeTester(Path workspaceDirectory, Path classesDirectory, String mainClassName) {
        //include compiled classes & workspace resources > use current os's classpath separator
        String classpath = classesDirectory + java.io.File.pathSeparator + workspaceDirectory;
        ProcessBuilder builder = new ProcessBuilder(JavaTools.getRuntimePath().toString(), "-cp", classpath, mainClassName);
        builder.directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, executionTimeout, "java execution failed", outputMaxBytes);
    }

    private String outputLimitMessage(String stage, ProcessResult process) {
        String stream = process.isStandardOutputLimitExceeded()
                ? (process.isErrorOutputLimitExceeded() ? "stdout and stderr" : "stdout") : "stderr";
        return stage + " " + stream + " exceeded " + outputMaxBytes + " bytes; no marks awarded";
    }

    // Write diagnostics while process output is local; grades retain no raw output.
    private void logDiagnostics(String studentLabel, String questionId, String stage, ProcessResult process) {
        String errorOutput = process.getErrorOutput().strip();
        if (errorOutput.isEmpty()) return;
        Status level = process.isTimedOut() ? Status.WARNING : process.isSuccessful() ? Status.LOG : Status.ERROR;
        JsonLineLogger.getInstance().append(level, "QuestionGrader",
                studentLabel + " " + questionId + " > " + stage + " diagnostics > " + process + "\n" + errorOutput);
    }

    private QuestionGrade failedGrade(String questionId, GradeStatus status, String message) {
        return new QuestionGrade(questionId, status, 0, List.of(), message);
    }
}
