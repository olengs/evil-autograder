// Tests required-answer identity warnings and separation of missing and mismatched fields.
// Testing only; not required by the application.

package validation;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import model.SubmissionIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.SubmissionFileFinder;
import validation.rules.StudentHeaderRule;

class StudentHeaderRuleTest {
    @TempDir Path root;

    private List<SubmissionIssue> validate() {
        return new StudentHeaderRule().validate(submission(root),
                question(root.resolve("templates"), "q1", "Q1", List.of("Answer.java"), List.of("Helper.java")),
                new SubmissionFileFinder().forSubmission());
    }

    @Test
    void acceptsMatchingFieldsRegardlessOfCase() throws IOException {
        write(root, "Q1/Answer.java", "// Name: ALICE TAN\n// Email: ALICE@EXAMPLE.EDU\nclass Answer {}\n");
        assertTrue(validate().isEmpty());
    }

    @Test
    void missingAndBlankFieldsHaveExplicitWarnings() throws IOException {
        write(root, "Q1/Answer.java", "// Name:\nclass Answer {}\n");
        var issues = validate();
        assertEquals(2, issues.size());
        assertTrue(issues.stream().allMatch(issue -> issue.getCode().equals("missing student header")));
        assertTrue(issues.get(0).getMessage().contains("(blank)"));
        assertTrue(issues.get(1).getMessage().contains("(missing)"));
    }

    @Test
    void mismatchedValuesReportExpectedAndActualIdentity() throws IOException {
        write(root, "Q1/Answer.java", "// Name: Bob Tan\n// Email: bob@example.edu\nclass Answer {}\n");
        var issues = validate();
        assertEquals(2, issues.size());
        assertEquals("mismatched student header", issues.getFirst().getCode());
        assertTrue(issues.getFirst().getMessage().contains("expected [Alice Tan] but found [Bob Tan]"));
    }

    @Test
    void validatesRecoveredAnswerButDoesNotInspectOptionalResources() throws IOException {
        Path answer = write(root, "renamed/answer.java", "// Name: Bob\n// Email: alice@example.edu\n");
        write(root, "Q1/Helper.java", "class Helper {}\n");
        var issues = validate();
        assertEquals(1, issues.size());
        assertEquals(answer, issues.getFirst().getAffectedPath());
    }

    @Test
    void missingRequiredFileDoesNotDuplicateStructuralWarning() {
        assertTrue(validate().isEmpty());
    }
}
