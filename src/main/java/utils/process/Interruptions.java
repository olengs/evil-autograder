package utils.process;

import java.io.InterruptedIOException;

public final class Interruptions { //checks cancellation while preserving thread's interrupt flag
    private Interruptions() {}

    public static void check(String message) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(message);
    }
}
