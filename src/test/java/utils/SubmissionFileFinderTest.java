// Tests nested and relocated answer lookup, ambiguity, and submission-scoped caching.
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

class SubmissionFileFinderTest {
    @TempDir Path root;
    private final SubmissionFileFinder files = new SubmissionFileFinder();

    @Test
    void prefersDeclaredQuestionFolderOverRelocatedDuplicate() throws IOException {
        Path expected = write(root, "Q1/Answer.java", "class Answer {}\n");
        write(root, "elsewhere/Answer.java", "wrong copy");
        assertEquals(expected, files.findAnswerFile(root, "Q1", "Answer.java").orElseThrow());
    }

    @Test
    void findsNestedRequiredPathIgnoringFolderAndFileCase() throws IOException {
        Path actual = write(root, "q1/SRC/answer.JAVA", "class Answer {}\n");
        assertEquals(actual, files.findAnswerFile(root, "Q1", "src/Answer.java").orElseThrow());
        assertTrue(files.findExact(root.resolve("q1"), "src/Answer.java").isEmpty());
    }

    @Test
    void recoversAnUnambiguousRelocatedAnswerAsAbsolutePath() throws IOException {
        Path actual = write(root, "outer/renamed/Answer.java", "class Answer {}\n");
        assertEquals(List.of(actual), files.findAnswerFiles(root, "Q1", List.of("Answer.java")));
        assertTrue(actual.isAbsolute());
    }

    @Test
    void refusesAmbiguousRelocatedAnswers() throws IOException {
        write(root, "first/Answer.java", "one");
        write(root, "second/Answer.java", "two");
        IOException error = assertThrows(IOException.class, () -> files.findAnswerFile(root, "Q1", "Answer.java"));
        assertTrue(error.getMessage().contains("ambiguous answer file"));
    }

    @Test
    void excludesHiddenFoldersFromRelocatedSearch() throws IOException {
        write(root, ".backup/Answer.java", "backup");
        assertTrue(files.findAnswerFile(root, "Q1", "Answer.java").isEmpty());
    }

    @Test
    void rejectsTraversalInDeclaredRelativePaths() throws IOException {
        Files.createDirectory(root.resolve("Q1"));
        assertThrows(IOException.class, () -> files.findAnswerFile(root, "Q1", "../Answer.java"));
        assertFalse(files.matchesRequiredPath(root.resolve("Answer.java"), "../Answer.java"));
    }

    @Test
    void lookupCacheLivesWithinOneValidationOnly() throws IOException {
        var currentSubmission = files.forSubmission();
        assertTrue(currentSubmission.findAnswerFile(root, "Q1", "Answer.java").isEmpty());
        Path added = write(root, "Q1/Answer.java", "class Answer {}\n");
        assertTrue(currentSubmission.findAnswerFile(root, "Q1", "Answer.java").isEmpty());
        assertEquals(added, files.forSubmission().findAnswerFile(root, "Q1", "Answer.java").orElseThrow());
    }

    @Test
    void requiredNestedPathMustMatchWholeSuffix() {
        assertTrue(files.matchesRequiredPath(root.resolve("renamed/src/Answer.java"), "src/Answer.java"));
        assertFalse(files.matchesRequiredPath(root.resolve("renamed/other/Answer.java"), "src/Answer.java"));
    }
}
