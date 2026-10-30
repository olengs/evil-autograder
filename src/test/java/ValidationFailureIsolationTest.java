import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validation.SubmissionFileFinder;
import validation.SubmissionValidationRule;
import validation.SubmissionValidator;
import validation.ValidationRule;

/** Filesystem failures are recorded at their known scope without swallowing programming errors. */
class ValidationFailureIsolationTest {
    @TempDir
    Path temp;

    @Test
    void questionReadFailureKeepsOtherQuestionsAndStudentsValidWithoutDuplicatingInheritedIssues()
            throws IOException {
        StudentSubmission first = submission("first");
        StudentSubmission next = submission("next");
        SubmissionIssue upstream = new SubmissionIssue("normalization note", IssueSeverity.WARNING,
                "Submission root corrected", null, first.getRootDirectory(), "normalization", true);
        first.addIssues(List.of(upstream));
        ValidationRule failingRule = (student, question) -> {
            if (student == first && question.getId().equals("Q1")) {
                throw new UncheckedIOException(new IOException("directory enumeration failed"));
            }
            return List.of();
        };
        SubmissionValidator validator = new SubmissionValidator(
                List.of(failingRule), List.of(), new SubmissionFileFinder());

        assertSame(first, validator.validate(first, assessment()));
        assertSame(next, validator.validate(next, assessment()));

        assertEquals(2, first.getSubmissionIssues().size());
        SubmissionIssue failure = onlyNewIssue(first, upstream);
        assertFailure(failure, "Q1", first.getRootDirectory().resolve("Q1"));
        assertFalse(attempt(first, "Q1").isValid());
        assertTrue(attempt(first, "Q1").getIssues().contains(failure));
        assertEquals(List.of(upstream), attempt(first, "Q2").getIssues());
        assertTrue(attempt(first, "Q2").isValid());
        assertEquals(1, first.getSubmissionIssues().stream().filter(issue -> issue == upstream).count());
        assertEquals(1, first.getSubmissionIssues().stream().filter(issue -> issue == failure).count());
        assertHealthy(next);
    }

    @Test
    void submissionAccessFailureHasGlobalScopeAndDoesNotInterruptNextStudent() throws IOException {
        StudentSubmission first = submission("first");
        StudentSubmission next = submission("next");
        SubmissionValidationRule failingRule = (student, assessment) -> {
            if (student == first) throw new SecurityException("directory access denied");
            return List.of();
        };
        SubmissionValidator validator = new SubmissionValidator(
                List.of(), List.of(failingRule), new SubmissionFileFinder());

        validator.validate(first, assessment());
        validator.validate(next, assessment());

        assertEquals(1, first.getSubmissionIssues().size());
        SubmissionIssue failure = first.getSubmissionIssues().getFirst();
        assertFailure(failure, null, first.getRootDirectory());
        assertEquals(2, first.getQuestionAttempts().size());
        for (QuestionAttempt attempt : first.getQuestionAttempts()) {
            assertFalse(attempt.isValid());
            assertEquals(List.of(failure), attempt.getIssues());
        }
        assertHealthy(next);
    }

    @Test
    void answerFileLookupFailureIsScopedToItsQuestionAndDoesNotInterruptNextStudent() throws IOException {
        StudentSubmission first = submission("first");
        StudentSubmission next = submission("next");
        Path unreadableFolder = first.getRootDirectory().resolve("Q1");
        SubmissionFileFinder finder = new SubmissionFileFinder() {
            @Override
            public List<Path> findSubmissionFiles(Path directory, List<String> requiredFiles) throws IOException {
                if (directory.equals(unreadableFolder)) {
                    throw new UncheckedIOException(new IOException("answer listing failed"));
                }
                return super.findSubmissionFiles(directory, requiredFiles);
            }
        };
        SubmissionValidator validator = new SubmissionValidator(List.of(), List.of(), finder);

        validator.validate(first, assessment());
        validator.validate(next, assessment());

        assertEquals(1, first.getSubmissionIssues().size());
        SubmissionIssue failure = first.getSubmissionIssues().getFirst();
        assertFailure(failure, "Q1", unreadableFolder);
        assertFalse(attempt(first, "Q1").isValid());
        assertEquals(List.of(failure), attempt(first, "Q1").getIssues());
        assertTrue(attempt(first, "Q1").getSubmittedFiles().isEmpty());
        assertTrue(attempt(first, "Q2").isValid());
        assertEquals(List.of(first.getRootDirectory().resolve("Q2/Q2.java")),
                attempt(first, "Q2").getSubmittedFiles());
        assertHealthy(next);
    }

    @Test
    void programmingErrorInRuleIsStillVisibleToCaller() throws IOException {
        StudentSubmission submission = submission("student");
        IllegalStateException bug = new IllegalStateException("invalid rule state");
        ValidationRule brokenRule = (student, question) -> { throw bug; };
        SubmissionValidator validator = new SubmissionValidator(
                List.of(brokenRule), List.of(), new SubmissionFileFinder());

        assertSame(bug, assertThrows(IllegalStateException.class,
                () -> validator.validate(submission, assessment())));
        assertTrue(submission.getSubmissionIssues().isEmpty(),
                "A programming error must not be mislabeled as a student filesystem issue");
    }

    private AssessmentDefinition assessment() {
        Path resources = temp.resolve("templates");
        return new AssessmentDefinition("test", List.of(
                new QuestionDefinition("Q1", "Q1", resources, List.of("Q1.java"), List.of()),
                new QuestionDefinition("Q2", "Q2", resources, List.of("Q2.java"), List.of())));
    }

    private StudentSubmission submission(String username) throws IOException {
        Path root = temp.resolve(username).toAbsolutePath().normalize();
        for (String question : List.of("Q1", "Q2")) {
            Files.createDirectories(root.resolve(question));
            Files.writeString(root.resolve(question).resolve(question + ".java"),
                    "public class " + question + " {}\n");
        }
        StudentDetails student = new StudentDetails(username, username, username + "@school.edu");
        return new StudentSubmission(student, temp.resolve(username + ".zip"), root);
    }

    private static QuestionAttempt attempt(StudentSubmission submission, String id) {
        return submission.getQuestionAttempts().stream()
                .filter(attempt -> attempt.getQuestionDefinition().getId().equals(id)).findFirst().orElseThrow();
    }

    private static SubmissionIssue onlyNewIssue(StudentSubmission submission, SubmissionIssue inherited) {
        List<SubmissionIssue> added = submission.getSubmissionIssues().stream()
                .filter(issue -> issue != inherited).toList();
        assertEquals(1, added.size());
        return added.getFirst();
    }

    private static void assertFailure(SubmissionIssue issue, String questionId, Path affectedPath) {
        assertEquals(IssueSeverity.ERROR, issue.getSeverity());
        assertEquals("validation", issue.getStage());
        assertFalse(issue.isResolved());
        assertEquals(questionId, issue.getQuestionId());
        assertEquals(affectedPath, issue.getAffectedPath());
        assertFalse(issue.getMessage().isBlank());
    }

    private static void assertHealthy(StudentSubmission submission) {
        assertTrue(submission.getSubmissionIssues().isEmpty());
        assertEquals(2, submission.getQuestionAttempts().size());
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            assertTrue(attempt.isValid());
            assertEquals(1, attempt.getSubmittedFiles().size());
        }
    }
}
