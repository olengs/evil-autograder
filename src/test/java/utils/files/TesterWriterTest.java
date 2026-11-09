package utils.files;

import static org.junit.jupiter.api.Assertions.*;
import assessment.TesterLoader;
import generation.QuestionSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesterWriterTest {
    @TempDir Path output;

    @Test
    void replacesOldLayoutAndMetadataAndUpsertsCounts() throws Exception {
        var a = new QuestionSpec("Q1a", "class Q1a {}", Path.of("resources/Q1"), List.of());
        var b = new QuestionSpec("Q1b", "class Q1b {}", Path.of("resources/Q1"), List.of());
        Files.createDirectories(output.resolve("Q1"));
        Files.writeString(output.resolve("Q1aTester.java"), "old flat source");
        Files.writeString(output.resolve("Q1/Q1aTester.java"), "old nested source");
        Files.writeString(output.resolve("marks.txt"), "Q1a:99\n");
        Files.writeString(output.resolve("numTestCases.txt"), "Q1a:99\n");
        Files.writeString(output.resolve("Q1/data.txt"), "keep data");
        TesterWriter.prepareOutput(List.of(a, b), Map.of("q1a", 3.0, "q1b", 4.0), output);
        assertEquals("Q1a:3\nQ1b:4\n", Files.readString(output.resolve("marks.txt")));
        assertEquals("", Files.readString(output.resolve("numTestCases.txt")));
        assertFalse(Files.exists(output.resolve("Q1aTester.java")));
        assertFalse(Files.exists(output.resolve("Q1/Q1aTester.java")));
        TesterWriter.writeValidated(a, "class Q1aTester {}", 6, output);
        TesterWriter.writeValidated(b, "class Q1bTester {}", 8, output);
        TesterWriter.writeValidated(a, "class Q1aTester { int updated; }", 9, output);
        assertEquals("Q1b:8\nQ1a:9\n", Files.readString(output.resolve("numTestCases.txt")));
        assertTrue(Files.readString(output.resolve("Q1/Q1aTester.java")).contains("updated"));
        assertEquals("keep data", Files.readString(output.resolve("Q1/data.txt")));
        var loaded = TesterLoader.load(output);
        assertEquals(9, loaded.get("q1a").getNumTestCases());
        assertEquals(3, loaded.get("q1a").getMaximumMarks());
        assertEquals(8, loaded.get("q1b").getNumTestCases());
    }
}
