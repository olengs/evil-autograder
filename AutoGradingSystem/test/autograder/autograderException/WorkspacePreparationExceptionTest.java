package autograder.autograderException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link WorkspacePreparationException}. */
class WorkspacePreparationExceptionTest {

    @Test
    @DisplayName("message-only constructor preserves message, null code")
    void messageOnly() {
        WorkspacePreparationException ex = new WorkspacePreparationException("boom");
        assertEquals("boom", ex.getMessage());
        assertNull(ex.getCode());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("message+cause constructor preserves both, null code")
    void messageAndCause() {
        Throwable cause = new IllegalStateException("root");
        WorkspacePreparationException ex = new WorkspacePreparationException("boom", cause);
        assertEquals("boom", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getCode());
    }

    @Test
    @DisplayName("code+message overload preserves code")
    void codeAndMessage() {
        WorkspacePreparationException ex =
                new WorkspacePreparationException("wsp-001", "cannot create");
        assertEquals("wsp-001", ex.getCode());
        assertEquals("cannot create", ex.getMessage());
    }

    @Test
    @DisplayName("code+message+cause overload preserves all three")
    void codeMessageCause() {
        Throwable cause = new java.io.IOException("disk");
        WorkspacePreparationException ex =
                new WorkspacePreparationException("wsp-005", "cleanup failed", cause);
        assertEquals("wsp-005", ex.getCode());
        assertEquals("cleanup failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
