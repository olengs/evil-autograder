//checks cancellation without clearing the current thread's interrupt flag

package utils.process;

import java.io.InterruptedIOException;

public final class Interruptions {
    private Interruptions() {}

    public static void check(String message) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException(message);
    }
}
