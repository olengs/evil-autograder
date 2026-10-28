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
import model.ProcessResult;

public class ProcessExecutor {

    private static final ExecutorService drainers = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());

    public static ProcessResult executeProcess(ProcessBuilder builder, Duration timeout,
                                        String startErrorCode, String startErrorMsg) {
        
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new ProcessExecutionException(startErrorCode, startErrorMsg, e);
        }

        // Stream has limited memory, using sub-processes to drain them can prevent deadlock situation.
        // When process is generating large amount of data into the stream during execution.
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
        }
    }

    private static void destroyProcessTree(Process process) {
        // kill child first then parent
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /** Reads a stream fully as UTF-8 text with {@code try-with-resource} statement. */
    private static String readStream(InputStream in) {
        try (InputStream s = in) {
            return new String(s.readAllBytes(), StandardCharsets.UTF_8);
        } 
        // gracefully return the thread back to executor even if error occurs.
        catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        return "";
    }

    /**
    * Retrieves a drained stream result and map any capture failure.
    */
    private static String collectStream(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            // restore interrupted status of thread for higher up caller to terminate gracefully
            Thread.currentThread().interrupt(); 
            throw new ProcessExecutionException("prc-004", "Worker interrupted while capturing process output", e);
        } catch (ExecutionException e) {
            // error thrown by background threads
            throw new ProcessExecutionException("prc-003", "Stream capture failed", e);
        }
    }

    /** Best-effort stream retrieval used on the timeout path; never throws. */
    private static String safeGet(Future<String> future) {
        try {
            // timeout to prevent secondary hang caused by .get()
            return future.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
        }
        return "";
    }
}
