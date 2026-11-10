package utils.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;

public final class ProcessExecutor { //runs timed processes, captures output & terminates process trees
    private ProcessExecutor() {}

    public static final long DEFAULT_OUTPUT_MAX_BYTES = 1_048_576;

    // using virtual thread instead of fixed thread pool because the threads spend most of their time waiting for output.
    public static ExecutorService outputReaderThreads = Executors.newVirtualThreadPerTaskExecutor();

    private record CapturedOutput(String output, boolean limitExceeded, IOException failure) {}

    public static ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String startErrorMessage,
            long outputMaxBytes) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Process timeout must be positive");
        }

        if (outputMaxBytes <= 0) {
            throw new IllegalArgumentException("positive output limit required");
        }

        Process process;
        try {
            // Own both pipes so each stream is captured separately and bounded.
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE).redirectErrorStream(false);
            process = processBuilder.start();
        } catch (IOException error) {
            throw new ProcessExecutionException(startErrorMessage, error);
        }
        

        Future<CapturedOutput> standardOutputTask = null;
        Future<CapturedOutput> errorOutputTask = null;
        try {
            process.getOutputStream().close();
            //drain both streams while process runs so a full pipe cannot block it
            standardOutputTask = outputReaderThreads.submit(() -> captureStream(process.getInputStream(), outputMaxBytes, process));
            errorOutputTask = outputReaderThreads.submit(() -> captureStream(process.getErrorStream(), outputMaxBytes, process));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
            }

            CapturedOutput standardOutput = collectStream(standardOutputTask);
            CapturedOutput errorOutput = collectStream(errorOutputTask);
            // Killing an over-limit process can interrupt the other stream's read.
            if (!standardOutput.limitExceeded() && !errorOutput.limitExceeded()) {
                IOException failure = standardOutput.failure() != null ? standardOutput.failure() : errorOutput.failure();
                if (failure != null) throw new ProcessExecutionException("Output capture failed", failure);
            }
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            return new ProcessResult(exitCode, standardOutput.output(), errorOutput.output(), !finished,
                    standardOutput.limitExceeded(), errorOutput.limitExceeded());
        } catch (InterruptedException error) {
            //preserve interruption so caller can stop its own work
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException("Process run interrupted", error);
        } catch (IOException error) {
            throw new ProcessExecutionException("Cannot close process input", error);
        } finally {
            try {
                destroyProcessTree(process);
            } finally {
                IOUtils.closeQuietly(process.getInputStream());
                IOUtils.closeQuietly(process.getErrorStream());
                IOUtils.closeQuietly(process.getOutputStream());
                if (standardOutputTask != null) standardOutputTask.cancel(true);
                if (errorOutputTask != null) errorOutputTask.cancel(true);
            }
        }
    }

    public static void cleanup() {
        outputReaderThreads.shutdownNow();
    }

    // Drain each pipe while the process runs; callers parse the collected text afterward.
    private static CapturedOutput captureStream(InputStream input, long outputMaxBytes, Process process) throws IOException {
        boolean exceeded = false;
        IOException failure = null;
        try (var output = new ByteArrayOutputStream()) {
            try {
                long copied = IOUtils.copyLarge(input, output, 0, outputMaxBytes);
                // Exactly reaching the limit is valid; one extra byte means it was exceeded.
                if (copied == outputMaxBytes && input.read() != -1) {
                    exceeded = true;
                    destroyProcessTree(process);
                }
            } catch (IOException error) {
                failure = error;
            }
            return new CapturedOutput(output.toString(StandardCharsets.UTF_8), exceeded, failure);
        }
    }

    private static CapturedOutput collectStream(Future<CapturedOutput> future) throws InterruptedException {
        try {
            //a child may keep a pipe open > output collection also needs a bounded wait
            return future.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            throw new ProcessExecutionException("Output capture failed", error.getCause());
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new ProcessExecutionException("Output capture did not finish", error);
        }
    }

    private static void destroyProcessTree(Process process) {
        //stop descendants before parent so a timed-out tester cannot leave child processes running
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        //forced termination is asynchronous > wait for exit even when caller was interrupted
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            while (process.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new ProcessExecutionException("Process did not stop after forced termination");
                try {
                    process.waitFor(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException error) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

}
