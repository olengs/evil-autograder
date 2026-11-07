//runs an external process with a time limit while capturing its standard & error output
//drains both output streams & stops timed-out process trees & returns recorded process result

package utils.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.IOUtils;

public final class ProcessExecutor {
    private ProcessExecutor() {}

    private static String outputReaderPrefix(long workerId) {
        return "autograder-process-output-" + workerId + "-";
    }

    public static boolean isOutputReader(String threadName, long workerId) {
        return threadName.startsWith(outputReaderPrefix(workerId));
    }

    public static ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String startErrorCode, String startErrorMessage) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Process timeout must be positive");
        }

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException error) {
            throw new ProcessExecutionException(startErrorCode, startErrorMessage, error);
        }

        ExecutorService outputReaders = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().name(outputReaderPrefix(Thread.currentThread().threadId()), 0).factory());
        try {
            process.getOutputStream().close();
            //drain both streams while process runs so a full pipe cannot block it
            Future<String> standardOutputTask = outputReaders.submit(() -> readStream(process.getInputStream()));
            Future<String> errorOutputTask = outputReaders.submit(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                //give output readers time to collect buffered text after termination
                process.waitFor(2, TimeUnit.SECONDS);
            }

            String standardOutput = collectStream(standardOutputTask);
            String errorOutput = collectStream(errorOutputTask);
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            return new ProcessResult(exitCode, standardOutput, errorOutput, !finished);
        } catch (InterruptedException error) {
            //preserve interruption so caller can stop its own work
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException("prc-004", "Process run interrupted", error);
        } catch (IOException error) {
            throw new ProcessExecutionException("prc-003", "Cannot close process input", error);
        } finally {
            destroyProcessTree(process);
            IOUtils.closeQuietly(process.getInputStream());
            IOUtils.closeQuietly(process.getErrorStream());
            IOUtils.closeQuietly(process.getOutputStream());
            outputReaders.shutdownNow();
        }
    }


    private static String readStream(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    private static String collectStream(Future<String> future) throws InterruptedException {
        try {
            //a child may keep a pipe open > output collection also needs a bounded wait
            return future.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            throw new ProcessExecutionException("prc-003", "Output capture failed", error.getCause());
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new ProcessExecutionException("prc-003", "Output capture did not finish", error);
        }
    }

    private static void destroyProcessTree(Process process) {
        //stop descendants before parent so a timed-out tester cannot leave child processes running
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

}
