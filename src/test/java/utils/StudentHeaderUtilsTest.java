// Tests leading-comment identity parsing and trusted-template header detection.
// Testing only; not required by the application.

package utils;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.write;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentHeaderUtilsTest {
    @TempDir Path directory;

    @Test
    void readsCaseInsensitiveLabelsAndTrimsValues() throws IOException {
        Path file = write(directory, "Answer.java", "/*\n * nAmE: Alice Tan  \n * EMAIL ID: alice@example.edu\n */\nclass Answer {}\n");
        var header = StudentHeaderUtils.read(file);
        assertEquals("Alice Tan", header.name());
        assertEquals("alice@example.edu", header.email());
    }

    @Test
    void bothBlankTemplateLabelsStillIdentifyEditableFile() throws IOException {
        Path file = write(directory, "Answer.java", "// Name:\n// Email:\nclass Answer {}\n");
        assertTrue(StudentHeaderUtils.hasStudentHeader(file));
        assertEquals("", StudentHeaderUtils.read(file).name());
    }

    @Test
    void oneLabelAloneDoesNotIdentifyEditableFile() throws IOException {
        assertFalse(StudentHeaderUtils.hasStudentHeader(write(directory, "Name.java", "// Name: Alice\n")));
        assertFalse(StudentHeaderUtils.hasStudentHeader(write(directory, "Email.java", "// Email: alice@example.edu\n")));
    }

    @Test
    void ignoresIdentityLabelsAfterJavaCodeStarts() throws IOException {
        Path file = write(directory, "Answer.java", "package example;\n// Name: Alice\n// Email: alice@example.edu\n");
        assertNull(StudentHeaderUtils.read(file).name());
        assertFalse(StudentHeaderUtils.hasStudentHeader(file));
    }

    @Test
    void preservesFifteenLineHeaderLimit() throws IOException {
        Path file = write(directory, "Answer.java", "// intro\n".repeat(14) + "// Name: Alice\n// Email: alice@example.edu\n");
        assertEquals("Alice", StudentHeaderUtils.read(file).name());
        assertNull(StudentHeaderUtils.read(file).email());
        assertFalse(StudentHeaderUtils.hasStudentHeader(file));
    }

    @Test
    void doesNotTreatAnIncompleteBlockCommentAsHeader() throws IOException {
        Path file = write(directory, "Answer.java", "/*\nName: Alice\nEmail: alice@example.edu\n" + "intro\n".repeat(12) + "*/\n");
        assertFalse(StudentHeaderUtils.hasStudentHeader(file));
    }

    @Test
    void unreadableTemplateIsReportedToCaller() {
        assertThrows(IOException.class, () -> StudentHeaderUtils.hasStudentHeader(directory.resolve("missing.java")));
    }
}
