package utils;

import exceptions.ProcessExecutionException;
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
import model.ProcessResult;

public class ProcessExecutor {
    public static ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String startErrorCode, String startErrorMessage) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Process timeout must be positive");
        }

        Process process;
        try {
            process = processBuilder.start(); //start process > wrap any start failure
        } catch (IOException error) {
            throw new ProcessExecutionException(startErrorCode, startErrorMessage, error);
        }

        ExecutorService outputReaders = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            process.getOutputStream().close();
            //streams have limited memory > background threads drain them to prevent deadlock
            //when process is generating large amounts of data during execution
            Future<String> standardOutputTask = outputReaders.submit(() -> readStream(process.getInputStream()));
            Future<String> errorOutputTask = outputReaders.submit(() -> readStream(process.getErrorStream()));

            //enforce timeout
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                //wait briefly for the killed process to exit so the
                //drain threads can finish reading whatever was buffered
                process.waitFor(2, TimeUnit.SECONDS);
            }

            String standardOutput = collectStream(standardOutputTask);
            String errorOutput = collectStream(errorOutputTask);
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            return new ProcessResult(exitCode, standardOutput, errorOutput, !finished);
        } catch (InterruptedException error) {
            //restore interrupted status of thread for higher up caller to terminate gracefully
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException("prc-004", "Process run interrupted", error);
        } catch (IOException error) {
            throw new ProcessExecutionException("prc-003", "Cannot close process input", error);
        } finally {
            destroyProcessTree(process);
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            outputReaders.shutdownNow();
        }
    }

    //reads a stream fully as utf-8 text with try-with-resources
    private static String readStream(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    //retrieves a drained stream result & maps any capture failure
    private static String collectStream(Future<String> future) throws InterruptedException {
        try {
            //timeout to prevent secondary hang caused by .get()
            return future.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            //error thrown by background threads
            throw new ProcessExecutionException("prc-003", "Output capture failed", error.getCause());
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new ProcessExecutionException("prc-003", "Output capture did not finish", error);
        }
    }

    private static void destroyProcessTree(Process process) {
        //kill child first then parent
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            //cleanup must not replace the original run failure
        }
    }
}
