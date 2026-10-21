package runner;

import exceptions.ProcessExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import model.ProcessResult;

/**
 * Runs external {@code javac} and {@code java} processes for one prepared
 * grading workspace, enforcing a timeout and capturing output.
 *
 * Responsibilities:
 * <ul>
 *   <li>compile all {@code .java} files in a workspace with {@code javac}</li>
 *   <li>run a tester main class with {@code java}</li>
 *   <li>capture standard output and standard error <em>separately</em></li>
 *   <li>capture the exit code</li>
 *   <li>enforce a timeout and destroy the whole process tree when it is hit</li>
 * </ul>
 *
 * This class performs no grading: it does not parse test cases, calculate
 * marks, or map outcomes to grade statuses. It returns a raw
 * {@link ProcessResult} that the {@code QuestionGrader} will interpret.
 *
 * All compiler output and classpath entries are workspace-relative (output
 * goes to a {@code classes} sub-folder, classpath is {@code classes} and
 * {@code .}), so a run can never read or write outside its own workspace.
 *
 * Streams are drained concurrently on dedicated threads so that a process
 * producing large output cannot deadlock by filling an OS pipe buffer.
 */
public class ProcessRunner {

    /** Workspace-relative directory that receives compiled {@code .class} files. */
    private static final String CLASSES_DIR = "classes";

    /** Default application-wide execution limit used by the no-arg constructor. */
    private final Duration defaultTimeout;

    /** Effective execution limit enforced on every run. */
    private final Duration timeout;

    // TODO: modify this, not to hardcode
    /** Creates a runner with a application-wide default timeout (10s). */
    public ProcessRunner() {
        this.defaultTimeout = Duration.ofSeconds(10);
        this.timeout = this.defaultTimeout;
    }

    /** Creates a runner with the given execution timeout. */
    public ProcessRunner(Duration timeout) {
        // TODO: config file for user to auto config?
        this.defaultTimeout = Duration.ofSeconds(10);
        this.timeout = (timeout == null) ? this.defaultTimeout : timeout;
    }

    /**
     * Compiles every {@code .java} file found in {@code workspace} with
     * {@code javac} command, writing {@code .class} files to a {@code classes}
     * sub-folder inside the workspace.
     *
     * The command is equivalent to
     * {@code javac -d classes -cp . <sources>} run with the workspace as the
     * working directory. Both the output directory ({@code classes}) and the
     * compile classpath ({@code .}) are workspace-relative, so compilation
     * never reads from or writes to anywhere outside this attempt's workspace.
     * The {@code -cp .} entry lets the compile pick up any precompiled
     * {@code .class}/{@code .jar} resources the question shipped in its
     * resource folder.
     *
     * @param workspace prepared workspace containing sources to compile
     * @return the raw process result (exit code, output, timeout state)
     * @throws ProcessExecutionException prc-001 if {@code javac} cannot start,
     *         prc-003 on stream-capture failure, prc-004 if interrupted
     */
    public ProcessResult compile(Path workspace) {
        List<String> sources = findJavaFiles(workspace); // find all .java files in workspace

        List<String> command = new ArrayList<>(); // building javac command
        command.add(javacExecutable());
        command.add("-d");
        command.add(CLASSES_DIR); // output .class files into workspace/classes
        command.add("-cp");
        command.add("."); // compile classpath = workspace root only
        command.addAll(sources);

        ProcessBuilder builder = new ProcessBuilder(command); // execute javac command with .java files as arg
        builder.directory(workspace.toFile()); // making sure compilation done in workspace
        return executeProcess(builder, "prc-001", "javac could not be started");
    }

    /**
     * Runs the tester main class with {@code java} command, using the compiled output
     * in the workspace's {@code classes} sub-folder.
     *
     * The command is equivalent to
     * {@code java -cp classes<sep>. <mainClass>} run with the workspace as the
     * working directory, where {@code <sep>} is the platform classpath
     * separator. Both classpath entries ({@code classes} and {@code .}) are
     * workspace-relative: {@code classes} holds the compiled {@code .class}
     * files, and {@code .} makes any runtime resource files the tester reads
     * (and any precompiled resources) resolve from inside the workspace. The
     * tester therefore cannot load code or data from outside this attempt's
     * workspace.
     *
     * @param workspace prepared workspace that was previously compiled
     * @param mainClass tester main class to execute
     * @return the raw process result (exit code, output, timeout state)
     * @throws ProcessExecutionException prc-002 if {@code java} cannot start,
     *         prc-003 on stream-capture failure, prc-004 if interrupted
     */
    public ProcessResult runTester(Path workspace, String mainClass) {
        // run classpath = compiled classes dir first, then workspace root for resources.
        String runClasspath = CLASSES_DIR + java.io.File.pathSeparator + ".";

        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable(), "-cp", runClasspath, mainClass);
        builder.directory(workspace.toFile());
        return executeProcess(builder, "prc-002", "java could not be started");
    }

    // ------------------------------------------------------------------
    // Core execution
    // ------------------------------------------------------------------

    /**
     * Starts the process described by {@code builder}, drains stdout/stderr on
     * separate threads, and enforces {@link #timeout}. On timeout the whole
     * process tree is destroyed and the result is flagged timed-out.
     *
     * @param builder        configured process builder (working dir set)
     * @param startErrorCode error code to raise if the process cannot start
     * @param startErrorMsg  message to use if the process cannot start
     */
    private ProcessResult executeProcess(ProcessBuilder builder,
                                         String startErrorCode, String startErrorMsg) {
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new ProcessExecutionException(startErrorCode, startErrorMsg, e);
        }

        // Stream has limited memory, using sub-processes to drain them can prevent deadlock situation.
        // When process is generating large amount of data into the stream during execution.
        ExecutorService drainers = Executors.newFixedThreadPool(2);
        Future<String> outFuture = drainers.submit(() -> readStream(process.getInputStream()));
        Future<String> errFuture = drainers.submit(() -> readStream(process.getErrorStream()));

        try {
            // enforce timeout
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                // Wait briefly for the killed process to actually exit so the
                // drain threads can finish reading whatever was buffered.
                process.waitFor(2, TimeUnit.SECONDS);
                String out = safeGet(outFuture);
                String err = safeGet(errFuture);
                return new ProcessResult(process.exitValue(), out, err, true);
            }

            String out = collectStream(outFuture);
            String err = collectStream(errFuture);
            return new ProcessResult(process.exitValue(), out, err, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyProcessTree(process);
            throw new ProcessExecutionException(
                    "prc-004", "Worker interrupted while waiting for process", e);
        } finally {
            drainers.shutdownNow();
        }
    }

    /** Reads a stream fully as UTF-8 text with {@code try-with-resource} statement. */
    private String readStream(InputStream in) throws IOException {
        try (InputStream s = in) {
            return new String(s.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Retrieves a drained stream result and map any capture failure.
     */
    private String collectStream(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            // restore interrupted status of thread for higher up caller to terminate gracefully
            Thread.currentThread().interrupt(); 
            throw new ProcessExecutionException(
                    "prc-004", "Worker interrupted while capturing process output", e);
        } catch (ExecutionException e) {
            // error thrown by background threads
            throw new ProcessExecutionException(
                    "prc-003", "Stream capture failed", e.getCause() == null ? e : e.getCause());
        }
    }

    /** Best-effort stream retrieval used on the timeout path; never throws. */
    private String safeGet(Future<String> future) {
        try {
            // timeout to prevent secondary hang caused by .get()
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore exception
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    /** Destroys the process and all of its descendants forcefully using OS-level command. */
    private void destroyProcessTree(Process process) {
        // kill child first then parent
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** @return absolute path to the {@code javac} executable in the running JDK */
    private String javacExecutable() {
        return toolExecutable("javac");
    }

    /** @return absolute path to the {@code java} executable in the running JDK */
    private String javaExecutable() {
        return toolExecutable("java");
    }

    /** Resolves a JDK tool executable from {@code java.home/bin}. */
    private String toolExecutable(String tool) {
        String home = System.getProperty("java.home"); // finds system directory
        String exe = tool;
        // handle windows, require .exe
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            exe = tool + ".exe";
        }
        return Path.of(home, "bin", exe).toString(); // join file path suitable for OS
    }

    /** @return the relative names of all {@code .java} files under {@code workspace} */
    private List<String> findJavaFiles(Path workspace) {
        try (Stream<Path> walk = Files.walk(workspace)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> workspace.relativize(p).toString())
                    .toList();
        } catch (IOException e) {
            throw new ProcessExecutionException(
                    "prc-001", "Could not enumerate .java files to compile", e);
        }
    }
}
