// Tests required-answer checks and recoverable filename or folder differences.
// Testing only; not required by the application.

package validation;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import model.IssueSeverity;
import model.SubmissionIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.SubmissionFileFinder;
import validation.rules.AnswerFileRule;

class AnswerFileRuleTest {
    @TempDir Path root;

    private List<SubmissionIssue> validate() {
        return new AnswerFileRule().validate(submission(root),
                question(root.resolve("templates"), "q1", "Q1", List.of("Answer.java"), List.of()),
                new SubmissionFileFinder().forSubmission());
    }

    @Test
    void acceptsRequiredAnswerAtItsDeclaredPath() throws IOException {
        write(root, "Q1/Answer.java", "answer");
        assertTrue(validate().isEmpty());
    }

    @Test
    void missingFolderAndAnswerRemainSeparateErrors() {
        var issues = validate();
        assertEquals(List.of("missing question folder", "missing required file"),
                issues.stream().map(SubmissionIssue::getCode).toList());
        assertTrue(issues.stream().allMatch(issue -> issue.getSeverity() == IssueSeverity.ERROR));
        assertTrue(issues.stream().allMatch(issue -> issue.getQuestionId().equals("q1")));
    }

    @Test
    void wrongFilenameCapitalizationIsRecoverableWarning() throws IOException {
        Path answer = write(root, "Q1/answer.java", "answer");
        var issues = validate();
        assertEquals(1, issues.size());
        assertEquals("wrong file name", issues.getFirst().getCode());
        assertEquals(IssueSeverity.WARNING, issues.getFirst().getSeverity());
        assertEquals(answer, issues.getFirst().getAffectedPath());
    }

    @Test
    void relocatedAnswerProducesLayoutWarningsRatherThanMissingErrors() throws IOException {
        write(root, "renamed/Answer.java", "answer");
        var issues = validate();
        assertEquals(List.of("question folder layout", "wrong file name"),
                issues.stream().map(SubmissionIssue::getCode).toList());
        assertTrue(issues.stream().allMatch(issue -> issue.getSeverity() == IssueSeverity.WARNING));
    }

    @Test
    void ambiguousAnswerProducesOneReadIssueForThatAnswerLookup() throws IOException {
        Files.createDirectories(root.resolve("Q1"));
        write(root, "one/Answer.java", "one");
        write(root, "two/Answer.java", "two");
        var issues = validate();
        assertEquals(1, issues.size());
        assertEquals("file read error", issues.getFirst().getCode());
        assertTrue(issues.getFirst().getMessage().contains("ambiguous answer file"));
    }
}
