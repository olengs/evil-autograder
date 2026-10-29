import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validation.*;
import validation.rules.*;

class SubmissionValidationTest {
    @TempDir Path root;

    @Test
    void validAnswerKeepsItsQuestionAndSubmissionDirectory() throws Exception {
        Path folder = Files.createDirectories(root.resolve("Q1"));
        Path answer = Files.writeString(folder.resolve("Q1a.java"), "class Q1a {}");
        var attempt = validate().getQuestionAttempts().getFirst();

        assertEquals("Q1a", attempt.getQuestionDefinition().getId());
        assertEquals(folder, attempt.getAttempRootPath());
        assertEquals(List.of(answer), attempt.getAttemptFullPaths());
        assertTrue(attempt.isValid());
    }

    @Test
    void missingAnswerBlocksTheAttempt() throws Exception {
        Files.createDirectories(root.resolve("Q1"));
        var attempt = validate().getQuestionAttempts().getFirst();

        assertFalse(attempt.isValid());
        assertTrue(attempt.getIssues().stream().anyMatch(issue ->
                issue.getCode().equals("missing required file")));
    }

    @Test
    void unexpectedFileWarnsWithoutBlockingTheAnswer() throws Exception {
        Path folder = Files.createDirectories(root.resolve("Q1"));
        Files.writeString(folder.resolve("Q1a.java"), "class Q1a {}");
        Files.writeString(folder.resolve("extra.txt"), "extra");
        var submission = validate();

        assertTrue(submission.getQuestionAttempts().getFirst().isValid());
        assertTrue(submission.getSubmissionIssues().stream().anyMatch(issue ->
                issue.getCode().equals("unexpected file") && issue.getSeverity() == IssueSeverity.WARNING));
    }

    private StudentSubmission validate() {
        var question = new QuestionDefinition("Q1a", "Q1", List.of("Q1a.java"), List.of("support.txt"));
        var submission = new StudentSubmission(null, root.resolve("submission.zip"), root);
        var finder = new AnswerFileLocator();
        var validator = new SubmissionValidator(
                List.of(new QuestionFolderRule(), new RequiredFileRule(finder), new FileNamingRule(finder)),
                List.of(new UnexpectedFileRule()), finder);
        return validator.validate(submission, new AssessmentDefinition("sample", List.of(question)));
    }
}
