package utils.process;

import java.io.InterruptedIOException;
import java.util.concurrent.CancellationException;

public final class Interruptions { //checks cancellation while preserving thread's interrupt flag
    private Interruptions() {}

    public static void check(String message) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(message);
    }

    public static void check(Throwable failure, String message) throws InterruptedIOException {
        boolean interrupted = Thread.currentThread().isInterrupted();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            interrupted |= cause instanceof InterruptedException || cause instanceof InterruptedIOException
                    || cause instanceof CancellationException;
        }
        if (!interrupted) return;
        Thread.currentThread().interrupt();
        var error = new InterruptedIOException(message);
        error.initCause(failure);
        throw error;
    }
}
