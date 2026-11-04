// Tests shared direct-file enumeration without recursive scans or extension assumptions.
// Testing only; not required by the application.

package utils;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTest {
    @TempDir Path root;

    @Test
    void returnsOnlyDirectRegularFiles() throws IOException {
        Path direct = write(root, "answer.java", "answer");
        write(root, "nested/answer.java", "nested");
        Files.createDirectory(root.resolve("directory.zip"));
        try (var files = FileUtils.streamRegularFiles(root)) {
            assertEquals(List.of(direct), files.toList());
        }
    }

    @Test
    void emptyDirectoryProducesEmptyStream() throws IOException {
        try (var files = FileUtils.streamRegularFiles(root)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void missingDirectoryIsReportedToCaller() {
        assertThrows(IOException.class, () -> FileUtils.streamRegularFiles(root.resolve("missing")));
    }
}
