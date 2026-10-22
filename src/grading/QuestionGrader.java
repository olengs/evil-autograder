package grading;

import exceptions.ProcessExecutionException;
import java.time.Duration;
import model.GradeStatus;
import model.ProcessResult;
import model.QuestionGrade;
import model.QuestionTestPack;
import utils.JavaProcessUtils;
import utils.ProcessExecutor;

public class QuestionGrader implements AutoCloseable {
    
    private final GradingWorkspace workspace;
    private final static Duration defaultTimeout = Duration.ofSeconds(10);;
    private final Duration timeout;
    private final String questionId;
    private final String testerEntryPoint;

    public QuestionGrader(QuestionTestPack definitions) {
        this(definitions, defaultTimeout);
    }

    public QuestionGrader(QuestionTestPack definitions, Duration timeout) {
        this.questionId = definitions.getQuestionDefinition().getId();
        this.testerEntryPoint = definitions.getTesterDefinition().getEntryPoint();
        this.timeout = timeout;
        this.workspace = new GradingWorkspace();
        this.workspace.Prepare(definitions);
    }

    public QuestionGrade gradeQuestion() {
        ProcessResult compileResults = compile();
        if (!compileResults.isSuccessful() && !compileResults.isTimedOut()) {
            return new QuestionGrade(this.questionId, GradeStatus.COMPILE_ERROR, 0, null, compileResults, "Compilation failed");
        } else if (!compileResults.isTimedOut()) {
            return new QuestionGrade(this.questionId, GradeStatus.TIMEOUT, 0, null, compileResults, "Compilation timed out");
        }

        ProcessResult testerResults = executeTester(testerEntryPoint);
        if (!testerResults.isSuccessful() && !testerResults.isTimedOut()) {
            return new QuestionGrade(this.questionId, GradeStatus.RUNTIME_ERROR, 0, null, testerResults, "Testerfile encountered runtime error");
        } else if (!testerResults.isTimedOut()) {
            return new QuestionGrade(this.questionId, GradeStatus.TIMEOUT, 0, null, testerResults, "Testerfile timed out");
        }

        //TODO: Parse tester results and return valid scores.
        return new QuestionGrade(this.questionId, GradeStatus.COMPLETED, 100, null, testerResults, "Completed successfully");
    }

    /**
     * Compiles the java files in the workspace with {@code javac} command into the root folder
     * @return the raw process result (exit code, output, timeout state)
     * @throws ProcessExecutionException prc-001 if {@code javac} cannot start,
     *         prc-003 on stream-capture failure, prc-004 if interrupted
     */
    private ProcessResult compile() {
        // List<String> command = new ArrayList<>(); // building javac command
        // command.add(JavaProcessUtils.getJavacExecutablePath().toString());
        // currently only compile in workspace root only
        // command.add("-d");
        // command.add(CLASSES_DIR); // output .class files into workspace/classes
        // command.add("-cp");
        // command.add("."); // compile classpath = workspace root only
        // command.add("*.java");

        // assume all required files in question root
        ProcessBuilder builder = new ProcessBuilder(JavaProcessUtils.getJavacExecutablePath().toString(), "*.java");
        builder.directory(workspace.getWorkspaceRoot().toFile()); // making sure compilation done in workspace
        return ProcessExecutor.executeProcess(builder, timeout, "prc-001", "javac could not be started");
    }

    /**
     * Runs the tester main class with {@code java} command
     * @param mainClass tester main class to execute
     * @return the raw process result (exit code, output, timeout state)
     * @throws ProcessExecutionException prc-002 if {@code java} cannot start,
     *         prc-003 on stream-capture failure, prc-004 if interrupted
     */
    private ProcessResult executeTester(String mainClass) {
        // run classpath = compiled classes dir first, then workspace root for resources.
        // String runClasspath = CLASSES_DIR + java.io.File.pathSeparator + ".";

        ProcessBuilder builder = new ProcessBuilder(JavaProcessUtils.getJavaExecutablePath().toString(), mainClass);
        builder.directory(this.workspace.getWorkspaceRoot().toFile());
        return ProcessExecutor.executeProcess(builder, timeout, "prc-002", "java execution failed");
    }


    @Override
    public void close() throws Exception {
        this.workspace.close();
    }
}
