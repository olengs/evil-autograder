import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import submission.ZipExtractor;

class FileUtilsExtractionTest {
    @TempDir Path temp;

    @Test
    void extractsNestedFilesWithSpacesWithoutChangingTheArchive() throws IOException {
        Path archive = archive("Q1a/source files/Solution.java", "student answer");
        byte[] original = Files.readAllBytes(archive);
        Path root = ZipExtractor.extract(archive, temp.resolve("working copy"));
        assertEquals("student answer", Files.readString(root.resolve("Q1a/source files/Solution.java")));
        assertArrayEquals(original, Files.readAllBytes(archive));
    }

    @Test
    void rejectsTraversalWithEitherZipSeparator() throws IOException {
        for (String entry : new String[] {"../outside.java", "..\\outside.java"}) {
            Path archive = archive(entry, "outside");
            assertThrows(IOException.class, () -> ZipExtractor.extract(archive, temp.resolve("working")));
            assertFalse(Files.exists(temp.resolve("outside.java")));
        }
    }

    @Test
    void extractionNeverOverwritesAnExistingFile() throws IOException {
        Path archive = archive("Q1a.java", "replacement");
        Path destination = Files.createDirectories(temp.resolve("existing"));
        Files.writeString(destination.resolve("Q1a.java"), "original");
        assertThrows(IOException.class, () -> ZipExtractor.extract(archive, destination));
        assertEquals("original", Files.readString(destination.resolve("Q1a.java")));
    }

    private Path archive(String entry, String text) throws IOException {
        Path archive = Files.createTempFile(temp, "submission", ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }
}
