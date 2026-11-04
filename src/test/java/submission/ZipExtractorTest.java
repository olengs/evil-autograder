// Tests extraction containment, implicit parent creation, and empty archives.
// Testing only; not required by the application.

package submission;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipExtractorTest {
    @TempDir Path root;

    @Test
    void createsImplicitParentsAndReturnsAbsoluteExtractionRoot() throws IOException {
        Path archive = zip(root, "answers.zip", Map.of("Q1/src/Answer.java", "class Answer {}"));
        Path destination = root.resolve("workspace");
        assertEquals(destination.toAbsolutePath().normalize(), ZipExtractor.extract(archive, destination));
        assertEquals("class Answer {}", Files.readString(destination.resolve("Q1/src/Answer.java")));
    }

    @Test
    void refusesEntriesOutsideTheDestination() throws IOException {
        Path archive = zip(root, "escape.zip", Map.of("../outside.txt", "escape"));
        assertThrows(IOException.class, () -> ZipExtractor.extract(archive, root.resolve("workspace")));
        assertFalse(Files.exists(root.resolve("outside.txt")));
    }

    @Test
    void acceptsAValidEmptyArchive() throws IOException {
        Path archive = zip(root, "empty.zip", Map.of());
        Path destination = ZipExtractor.extract(archive, root.resolve("workspace"));
        try (var entries = Files.list(destination)) {
            assertEquals(0, entries.count());
        }
    }
}
