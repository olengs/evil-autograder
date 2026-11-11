package utils.process;

import java.io.InterruptedIOException;

/// checks cancellation while preserving thread's interrupt flag
public final class Interruptions { 
    private Interruptions() {}

    public static void check(String message) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(message);
    }
}
