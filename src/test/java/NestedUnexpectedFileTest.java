import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
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
import validation.SubmissionValidator;
import validation.rules.UnexpectedFileRule;

/** Nested extras are warnings for their folder's questions, never grounds for altering a submission. */
class NestedUnexpectedFileTest {
    @TempDir
    Path temp;

    @Test
    void nestedExtraWarnsBothQuestionsSharingFolderAndLeavesAllFileContentsIntact() throws IOException {
        Path resources = temp.resolve("templates");
        Path originalHelper = write(resources.resolve("Q1/Helper.java"), "class Helper { /* original */ }\n");
        Path root = temp.resolve("student");
        write(root.resolve("Q1/Q1a.java"), "class Q1a {}\n");
        write(root.resolve("Q1/Q1b.java"), "class Q1b {}\n");
        write(root.resolve("Q2/Q2.java"), "class Q2 {}\n");
        Path helper = write(root.resolve("Q1/Helper.java"), "class Helper { /* student modification */ }\n");
        Path extra = write(root.resolve("Q1/nested/Extra.java"), "class Extra {}\n");
        byte[] extraBefore = Files.readAllBytes(extra);
        byte[] helperBefore = Files.readAllBytes(helper);
        byte[] originalBefore = Files.readAllBytes(originalHelper);
        StudentSubmission submission = submission(root);
        AssessmentDefinition assessment = new AssessmentDefinition("shared-folder", List.of(
                new QuestionDefinition("Q1a", "Q1", resources, List.of("Q1a.java"), List.of("Helper.java")),
                new QuestionDefinition("Q1b", "Q1", resources, List.of("Q1b.java"), List.of("Helper.java")),
                new QuestionDefinition("Q2", "Q2", resources, List.of("Q2.java"), List.of())));

        new SubmissionValidator(List.of(), List.of(new UnexpectedFileRule()), new SubmissionFileFinder())
                .validate(submission, assessment);

        List<SubmissionIssue> issues = submission.getSubmissionIssues();
        assertEquals(2, issues.size());
        assertEquals(1, submission.getDistinctIssues().size());
        assertEquals(List.of("Q1a", "Q1b"), issues.stream().map(SubmissionIssue::getQuestionId).sorted().toList());
        for (SubmissionIssue issue : issues) {
            assertUnexpectedWarning(issue, extra);
            assertTrue(attempt(submission, issue.getQuestionId()).getIssues().contains(issue));
            assertEquals(1, submission.getSubmissionIssues().stream().filter(record -> record == issue).count());
        }
        assertTrue(attempt(submission, "Q2").getIssues().isEmpty());
        assertTrue(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));
        assertArrayEquals(extraBefore, Files.readAllBytes(extra));
        assertArrayEquals(helperBefore, Files.readAllBytes(helper));
        assertArrayEquals(originalBefore, Files.readAllBytes(originalHelper));
    }

    @Test
    void nestedRequiredResourcesAndEditableCompanionsAreRecognizedByRelativePath() throws IOException {
        Path root = temp.resolve("student");
        write(root.resolve("Q1a/src/Solution.java"), "class Solution {}\n");
        write(root.resolve("Q1a/data/input.txt"), "test input\n");
        write(root.resolve("Q1a/impl/Node.java"), "class Node {}\n");
        QuestionDefinition question = new QuestionDefinition("Q1a", "Q1a", temp.resolve("templates"),
                List.of("src/Solution.java"), List.of("data\\input.txt"))
                .withEditableFiles(List.of("src/Solution.java", "impl/Node.java"));
        AssessmentDefinition assessment = new AssessmentDefinition("nested-layout", List.of(question));

        List<SubmissionIssue> issues = new UnexpectedFileRule().validate(submission(root), assessment);

        assertTrue(issues.isEmpty(), "Declared nested paths, either separator, and editable companions are expected: " + issues);
    }

    @Test
    void declaredBasenameDoesNotAuthorizeSameFilenameInDifferentSubfolder() throws IOException {
        Path root = temp.resolve("student");
        write(root.resolve("Q1a/src/Solution.java"), "class Solution {}\n");
        Path extra = write(root.resolve("Q1a/other/Solution.java"), "class OtherSolution {}\n");
        byte[] before = Files.readAllBytes(extra);
        AssessmentDefinition assessment = new AssessmentDefinition("nested-layout", List.of(
                new QuestionDefinition("Q1a", "Q1a", temp.resolve("templates"),
                        List.of("src/Solution.java"), List.of())));

        List<SubmissionIssue> issues = new UnexpectedFileRule().validate(submission(root), assessment);

        assertEquals(1, issues.size());
        assertEquals("Q1a", issues.getFirst().getQuestionId());
        assertUnexpectedWarning(issues.getFirst(), extra);
        assertArrayEquals(before, Files.readAllBytes(extra));
    }

    private StudentSubmission submission(Path root) {
        return new StudentSubmission(new StudentDetails("Student", "student", "student@school.edu"),
                temp.resolve("student.zip"), root);
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private static QuestionAttempt attempt(StudentSubmission submission, String id) {
        return submission.getQuestionAttempts().stream()
                .filter(candidate -> candidate.getQuestionDefinition().getId().equals(id)).findFirst().orElseThrow();
    }

    private static void assertUnexpectedWarning(SubmissionIssue issue, Path path) {
        assertEquals("unexpected file", issue.getCode());
        assertEquals(IssueSeverity.WARNING, issue.getSeverity());
        assertEquals("validation", issue.getStage());
        assertFalse(issue.isResolved());
        assertEquals(path, issue.getAffectedPath());
    }
}
