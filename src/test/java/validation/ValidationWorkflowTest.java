// Tests issue aggregation, question isolation, and submission-wide extra-file warnings.
// Testing only; not required by the application.

package validation;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.StudentSubmission;
import model.SubmissionIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.SubmissionFileFinder;
import validation.rules.AnswerFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

class ValidationWorkflowTest {
    @TempDir Path root;

    private AssessmentDefinition assessment() {
        return new AssessmentDefinition("exam", List.of(
                question(root.resolve("templates"), "q1", "Q1", List.of("Answer.java"), List.of()),
                question(root.resolve("templates"), "q2", "Q2", List.of("Other.java"), List.of())));
    }

    private SubmissionValidator validator() {
        return new SubmissionValidator(List.of(new AnswerFileRule(), new StudentHeaderRule()),
                List.of(new UnexpectedFileRule()), new SubmissionFileFinder());
    }

    @Test
    void retainsPreparationIssuesAndAddsQuestionIssuesToSameSubmission() throws IOException {
        write(root, "Q1/Answer.java", HEADER + "class Answer {}\n");
        StudentSubmission submission = submission(root);
        SubmissionIssue preparation = new SubmissionIssue("preparation note", IssueSeverity.WARNING, "retained", null, root);
        submission.addIssues(List.of(preparation));
        assertSame(submission, validator().validate(submission, assessment()));
        assertEquals(2, submission.getQuestionAttempts().size());
        assertSame(preparation, submission.getSubmissionIssues().getFirst());
        assertTrue(submission.getQuestionAttempts().get(0).isValid());
        var secondIssues = submission.getQuestionAttempts().get(1).getIssues();
        assertFalse(secondIssues.isEmpty());
        assertTrue(submission.getSubmissionIssues().containsAll(secondIssues));
        assertTrue(secondIssues.stream().allMatch(issue -> issue.getQuestionId().equals("q2")));
    }

    @Test
    void aHeaderReadFailureDoesNotStopTheNextQuestion() throws IOException {
        Path invalid = write(root, "Q1/Answer.java", "");
        Files.write(invalid, new byte[] {(byte) 0xff});
        write(root, "Q2/Other.java", HEADER + "class Other {}\n");
        var submission = validator().validate(submission(root), assessment());
        assertFalse(submission.getQuestionAttempts().get(0).isValid());
        assertTrue(submission.getQuestionAttempts().get(0).getIssues().stream()
                .anyMatch(issue -> issue.getCode().equals("file read error")));
        assertTrue(submission.getQuestionAttempts().get(1).isValid());
    }

    @Test
    void refusesToAppendDuplicateAttemptsOnSecondValidation() {
        var submission = submission(root);
        var validator = validator();
        validator.validate(submission, assessment());
        assertThrows(IllegalStateException.class, () -> validator.validate(submission, assessment()));
        assertEquals(2, submission.getQuestionAttempts().size());
    }

    @Test
    void sharedFolderRulesCombineAllowedFilesAndWarnOnlyOnceForExtras() throws IOException {
        write(root, "Q1/Answer.java", HEADER);
        write(root, "Q1/Second.java", HEADER);
        write(root, "Q1/Helper.java", "helper");
        Path extra = write(root, "Q1/extra.txt", "extra");
        write(root, "Q1/nested/ignored.txt", "nested");
        var definition = new AssessmentDefinition("exam", List.of(
                question(root, "a", "Q1", List.of("Answer.java"), List.of("Helper.java")),
                question(root, "b", "Q1", List.of("Second.java"), List.of())));
        var issues = new UnexpectedFileRule().validate(submission(root), definition);
        assertEquals(1, issues.size());
        assertEquals("unexpected file", issues.getFirst().getCode());
        assertEquals(extra, issues.getFirst().getAffectedPath());
        assertNull(issues.getFirst().getQuestionId());
        assertEquals(IssueSeverity.WARNING, issues.getFirst().getSeverity());
    }

    @Test
    void globalPreparationErrorsStayOnSubmissionRatherThanQuestionIssueLists() throws IOException {
        write(root, "Q1/Answer.java", HEADER);
        write(root, "Q2/Other.java", HEADER);
        var submission = submission(root);
        var error = new SubmissionIssue("preparation error", IssueSeverity.ERROR, "global", null, root);
        submission.addIssues(List.of(error));
        validator().validate(submission, assessment());
        assertTrue(submission.getSubmissionIssues().contains(error));
        assertTrue(submission.getQuestionAttempts().stream().allMatch(attempt -> attempt.isValid()));
        assertTrue(submission.getQuestionAttempts().stream().allMatch(attempt -> !attempt.getIssues().contains(error)));
    }
}
