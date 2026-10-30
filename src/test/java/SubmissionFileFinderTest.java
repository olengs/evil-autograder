import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.FileNamingRule;
import validation.rules.RequiredFileRule;
import validation.rules.StudentHeaderRule;

/** Shared answer lookup retains exact-name priority and reports ambiguous or wrongly capitalized files. */
class SubmissionFileFinderTest {
    private static final StudentDetails STUDENT = new StudentDetails("Alice", "alice", "alice@school.edu");
    private static final String ANSWER = "src/Solution.java";

    @TempDir
    Path temp;

    @Test
    void exactAnswerWinsWithoutInvokingPotentiallyAmbiguousFallback() throws IOException {
        StudentSubmission submission = submission();
        Path folder = submission.getRootDirectory().resolve("Q1a");
        Path answer = write(folder.resolve(ANSWER));
        SubmissionFileFinder finder = new SubmissionFileFinder() {
            @Override
            public Optional<Path> findIgnoringCase(Path directory, String name) throws IOException {
                throw new IOException("fallback must not run when the exact answer exists");
            }
        };

        assertEquals(answer, finder.findCandidate(folder, ANSWER).orElseThrow());
        assertEquals(List.of(answer.toAbsolutePath().normalize()), finder.findSubmissionFiles(folder, List.of(ANSWER)));
        validator(finder).validate(submission, assessment());

        assertTrue(submission.getSubmissionIssues().isEmpty(), submission.getSubmissionIssues().toString());
        assertTrue(submission.getQuestionAttempts().getFirst().isValid());
        assertEquals(List.of(answer.toAbsolutePath().normalize()),
                submission.getQuestionAttempts().getFirst().getSubmittedFiles());
    }

    @Test
    void ambiguousFallbackRemainsAReadErrorInsteadOfBecomingAMissingAnswer() throws IOException {
        StudentSubmission submission = submission();
        Path folder = submission.getRootDirectory().resolve("Q1a");
        IOException ambiguity = new IOException("ambiguous filename > Solution.java");
        SubmissionFileFinder finder = new SubmissionFileFinder() {
            @Override
            public Optional<Path> findExact(Path directory, String name) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> findIgnoringCase(Path directory, String name) throws IOException {
                throw ambiguity;
            }
        };

        assertSame(ambiguity, assertThrows(IOException.class, () -> finder.findCandidate(folder, ANSWER)));
        assertSame(ambiguity, assertThrows(IOException.class,
                () -> finder.findSubmissionFiles(folder, List.of(ANSWER))));
        validator(finder).validate(submission, assessment());

        assertFalse(submission.getSubmissionIssues().isEmpty());
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            assertEquals("file read error", issue.getCode(), "Ambiguity must not be mislabeled as a missing answer");
            assertEquals(IssueSeverity.ERROR, issue.getSeverity());
            assertEquals("Q1a", issue.getQuestionId());
            assertEquals("validation", issue.getStage());
            assertTrue(issue.getMessage().contains("ambiguous filename"));
        }
        assertFalse(submission.getQuestionAttempts().getFirst().isValid());
        assertTrue(submission.getQuestionAttempts().getFirst().getSubmittedFiles().isEmpty());
    }

    @Test
    void nestedWrongCaseAnswerIsSelectedAndHeaderCheckedWhileFilenameErrorRemains() throws IOException {
        StudentSubmission submission = submission();
        Path answer = write(submission.getRootDirectory().resolve("Q1a/src/solution.java"));

        validator(new SubmissionFileFinder()).validate(submission, assessment());

        assertEquals(1, submission.getSubmissionIssues().size(), submission.getSubmissionIssues().toString());
        SubmissionIssue issue = submission.getSubmissionIssues().getFirst();
        assertEquals("wrong file name", issue.getCode());
        assertEquals(IssueSeverity.ERROR, issue.getSeverity());
        assertEquals("Q1a", issue.getQuestionId());
        assertEquals(answer, issue.getAffectedPath());
        var attempt = submission.getQuestionAttempts().getFirst();
        assertFalse(attempt.isValid());
        assertEquals(List.of(issue), attempt.getIssues());
        assertEquals(List.of(answer.toAbsolutePath().normalize()), attempt.getSubmittedFiles());
        assertEquals("solution.java", attempt.getSubmittedFiles().getFirst().getFileName().toString(),
                "Keep the actual submitted spelling even on case-insensitive filesystems");
    }

    private StudentSubmission submission() throws IOException {
        Path root = temp.resolve("student").toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("Q1a"));
        return new StudentSubmission(STUDENT, temp.resolve("alice.zip"), root);
    }

    private AssessmentDefinition assessment() {
        return new AssessmentDefinition("nested", List.of(
                new QuestionDefinition("Q1a", "Q1a", temp.resolve("templates"), List.of(ANSWER), List.of())));
    }

    private static SubmissionValidator validator(SubmissionFileFinder finder) {
        return new SubmissionValidator(List.of(new RequiredFileRule(finder), new FileNamingRule(finder),
                new StudentHeaderRule(finder)), List.of(), finder);
    }

    private static Path write(Path answer) throws IOException {
        Files.createDirectories(answer.getParent());
        Files.writeString(answer, "// Name: Alice\n// Email ID: alice@school.edu\npublic class Solution {}\n");
        return answer;
    }
}
