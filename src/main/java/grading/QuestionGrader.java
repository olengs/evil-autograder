package grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import model.GradeStatus;
import model.ProcessResult;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.QuestionGrade;
import model.TestCaseResult;
import model.TesterDefinition;
import utils.JavaProcessUtils;
import utils.ProcessExecutor;

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

    private ProcessResult compile(Path workspaceDirectory) throws IOException {
        //store compiled class files in classes folder
        Path classes = Files.createDirectories(workspaceDirectory.resolve("classes"));
        List<String> command = new ArrayList<>();
        command.add(JavaProcessUtils.getCompilerPath().toString());
        command.add("-d"); //destination for compiled classes
        command.add(classes.toString());
        command.add("-cp"); //where compiler looks for dependencies
        command.add(workspaceDirectory.toString());

        List<Path> sources;
        //find & sort java files directly inside workspace
        try (var files = Files.list(workspaceDirectory)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IOException("No Java sources in " + workspaceDirectory);
        }
        //each source path is one argument > supports filenames with spaces
        for (Path source : sources) {
            command.add(source.toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(workspaceDirectory.toFile());
        //run javac with compilation time limit
        return ProcessExecutor.run(builder, compilationTimeout, "prc-001", "javac could not be started");
    }

    private ProcessResult executeTester(Path workspaceDirectory, String mainClass) {
        //include compiled classes & workspace resources > separator depends on OS
        String classpath = workspaceDirectory.resolve("classes") + java.io.File.pathSeparator + workspaceDirectory;
        //run tester main class using java
        ProcessBuilder builder = new ProcessBuilder(JavaProcessUtils.getRuntimePath().toString(), "-cp", classpath, mainClass);
        builder.directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, executionTimeout, "prc-002", "java execution failed");
    }

    private QuestionGrade gradeInWorkspace(String questionId, String mainClassName, Path workspaceDirectory) throws IOException {
        //compile first > stop if compilation times out or fails
        ProcessResult compilationResult = compile(workspaceDirectory);

        if (compilationResult.isTimedOut()) {
            return failedGrade(questionId, GradeStatus.TIMEOUT, compilationResult, "compilation timed out");
        }
        if (!compilationResult.isSuccessful()) {
            return failedGrade(questionId, GradeStatus.COMPILE_ERROR, compilationResult, "compilation failed");
        }

        //run tester only after successful compilation
        ProcessResult executionResult = executeTester(workspaceDirectory, mainClassName);
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
