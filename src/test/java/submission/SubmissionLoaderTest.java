// Tests ZIP discovery, wrapper roots, retained failures, and roster identification.
// Testing only; not required by the application.

package submission;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import model.AssessmentDefinition;
import model.IssueSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmissionLoaderTest {
    @TempDir Path root;

    @Test
    void discoversSortedRegularZipFilesWithCaseInsensitiveExtensions() throws IOException {
        Path directory = Files.createDirectories(root.resolve("uploads"));
        Path second = zip(directory, "b.ZIP", Map.of());
        Path first = zip(directory, "a.zip", Map.of());
        write(directory, "notes.txt", "notes");
        Files.createDirectory(directory.resolve("folder.zip"));
        assertEquals(List.of(first, second), SubmissionLoader.findZipFiles(directory));
    }

    @Test
    void unwrapsSingleOuterFoldersAndPreservesOriginalArchive() throws IOException {
        Path archive = zip(root.resolve("uploads"), "alice.ZIP", Map.of("outer/renamed/Q1/Answer.java", HEADER));
        var assessment = new AssessmentDefinition("exam", List.of(
                question(root.resolve("templates"), "q1", "Q1", List.of("Answer.java"), List.of())));
        var submissions = SubmissionLoader.loadSubmissions(root.resolve("uploads"), root.resolve("workspace"),
                List.of(student()), assessment);
        assertEquals(1, submissions.size());
        var submission = submissions.getFirst();
        assertNotNull(submission.getStudent());
        assertEquals("alice", submission.getStudent().getUsername());
        assertEquals(archive, submission.getOriginalZip());
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q1/Answer.java")));
        assertTrue(submission.getSubmissionIssues().isEmpty());
    }

    @Test
    void repeatedLoadsUseDifferentTemporaryDirectories() throws IOException {
        zip(root.resolve("uploads"), "alice.zip", Map.of("Q1/Answer.java", "answer"));
        var first = SubmissionLoader.loadSubmissions(root.resolve("uploads"), root.resolve("workspace"), List.of(student())).getFirst();
        var second = SubmissionLoader.loadSubmissions(root.resolve("uploads"), root.resolve("workspace"), List.of(student())).getFirst();
        assertNotEquals(first.getRootDirectory(), second.getRootDirectory());
        assertTrue(Files.exists(first.getRootDirectory().resolve("Q1/Answer.java")));
        assertTrue(Files.exists(second.getRootDirectory().resolve("Q1/Answer.java")));
    }

    @Test
    void corruptArchiveIsRetainedAsFailedSubmissionAndOthersContinue() throws IOException {
        Path invalid = write(root, "uploads/alice.zip", "not a ZIP");
        zip(root.resolve("uploads"), "bob.zip", Map.of("Q1/Answer.java", "answer"));
        var submissions = SubmissionLoader.loadSubmissions(root.resolve("uploads"), root.resolve("workspace"), List.of(student()));
        assertEquals(2, submissions.size());
        var failed = submissions.stream().filter(submission -> submission.getOriginalZip().equals(invalid)).findFirst().orElseThrow();
        assertFalse(Files.exists(failed.getRootDirectory()));
        assertEquals("submission preparation error", failed.getSubmissionIssues().getFirst().getCode());
        assertEquals(IssueSeverity.ERROR, failed.getSubmissionIssues().getFirst().getSeverity());
        assertTrue(submissions.stream().anyMatch(submission -> Files.exists(submission.getRootDirectory().resolve("Q1/Answer.java"))));
    }

    @Test
    void unmatchedStudentIsRetainedWithSubmissionError() throws IOException {
        zip(root.resolve("uploads"), "stranger.zip", Map.of("Q1/Answer.java", "answer"));
        var submission = SubmissionLoader.loadSubmissions(root.resolve("uploads"), root.resolve("workspace"), List.of(student())).getFirst();
        assertNull(submission.getStudent());
        assertEquals("unknown student", submission.getSubmissionIssues().getFirst().getCode());
        assertTrue(Files.exists(submission.getRootDirectory()));
    }

    @Test
    void academicYearAndZipSuffixHandlingPreserveRosterMatching() {
        assertEquals("2026-2027-ALICE", SubmissionFileNames.baseNameWithoutZipExtension(Path.of("2026-2027-ALICE.ZIP")));
        assertEquals("alice", new StudentIdentifier().identify(Path.of("2026-2027-ALICE.ZIP"), List.of(student())).getUsername());
        assertTrue(SubmissionFileNames.hasZipExtension(Path.of("ALICE.ZIP")));
        assertFalse(SubmissionFileNames.hasZipExtension(Path.of("ALICE.zip.backup")));
        assertNull(new StudentIdentifier().identify(Path.of("unknown.zip"), List.of(student())));
    }
}
