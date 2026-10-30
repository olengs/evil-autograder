import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import submission.EditableFileDetector;
import submission.SubmissionNormalizer;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.FileNamingRule;
import validation.rules.QuestionFolderRule;
import validation.rules.RequiredFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

/** Checks that discovery, repair, and validation agree on the same identity section. */
class SharedHeaderWorkflowTest {
    private static final StudentDetails STUDENT = new StudentDetails(
            "Example Student", "example.student", "example.student@school.edu");
    private static final String BLANK_HEADER = "// Name:\n// Email ID:\n";

    @TempDir
    Path temp;

    @Test
    void longLeadingCommentsAreRecognisedByAllThreeStagesWithoutRepairs() throws IOException {
        String answer = longComment(STUDENT.getName(), STUDENT.getEmail()) + "public class Q1 {}\n";
        String companion = "class Companion {}\n";
        Flow flow = runWorkflow(answer, companion,
                longComment("", "") + "class Companion {}\n", List.of());

        assertEquals(List.of("Q1.java", "Companion.java"), flow.question().getEditableFiles());
        assertArrayEquals(answer.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(flow.answer()));
        assertArrayEquals(companion.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(flow.companion()));
        assertTrue(flow.submission().getSubmissionIssues().isEmpty(),
                flow.submission().getSubmissionIssues().toString());
        assertTrue(flow.attempt().isValid());
        assertTrue(flow.attempt().getSubmittedFiles().containsAll(List.of(flow.answer(), flow.companion())));
    }

    @Test
    void conflictingDuplicatesRemainUnchangedAndReachTheIssueHandoff() throws IOException {
        String answer = "// Name: " + STUDENT.getName() + "\n// Name: Another Student\n"
                + "// Email ID: " + STUDENT.getEmail() + "\n// Email: another@school.edu\n"
                + "public class Q1 {}\n";
        SubmissionIssue upstream = new SubmissionIssue("archive note", IssueSeverity.WARNING,
                "Earlier preparation finding", null, temp.resolve("example.student.zip"), "preparation", false);
        Flow flow = runWorkflow(answer, "class Companion {}\n", "class Companion {}\n", List.of(upstream));

        assertArrayEquals(answer.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(flow.answer()));
        List<SubmissionIssue> issues = flow.submission().getSubmissionIssues();
        assertEquals(3, issues.size(), issues.toString());
        assertSame(upstream, issues.getFirst());
        List<SubmissionIssue> conflicts = issues.stream()
                .filter(issue -> issue.getCode().equals("mismatched student header")).toList();
        assertEquals(2, conflicts.size(), "Matching first values must not hide later conflicting values");
        assertTrue(conflicts.stream().allMatch(issue -> issue.getStage().equals("validation")
                && issue.getSeverity() == IssueSeverity.WARNING && !issue.isResolved()
                && issue.getAffectedPath().equals(flow.answer()) && issue.getQuestionId().equals("Q1")));
        assertEquals(issues, flow.attempt().getIssues());
        assertTrue(flow.attempt().isValid(), "Identity warnings remain nonblocking");
    }

    @Test
    void commentsInsideCodeDoNotUnlockCompanionsOrPreventRequiredHeaderRepair() throws IOException {
        String bodyComments = "class Companion {\n// Name: " + STUDENT.getName()
                + "\n// Email ID: " + STUDENT.getEmail() + "\n}\n";
        String answer = "public class Q1 {\n// Name: " + STUDENT.getName()
                + "\n// Email ID: " + STUDENT.getEmail() + "\n}\n";
        Flow flow = runWorkflow(answer, bodyComments, bodyComments, List.of());

        assertEquals(List.of("Q1.java"), flow.question().getEditableFiles());
        assertEquals("// Name: " + STUDENT.getName() + "\n// Email ID: " + STUDENT.getEmail() + "\n" + answer,
                Files.readString(flow.answer()));
        assertArrayEquals(bodyComments.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(flow.companion()));
        List<SubmissionIssue> issues = flow.submission().getSubmissionIssues();
        assertEquals(1, issues.size(), issues.toString());
        SubmissionIssue repair = issues.getFirst();
        assertEquals("student header normalized", repair.getCode());
        assertEquals("normalization", repair.getStage());
        assertTrue(repair.isResolved());
        assertEquals(IssueSeverity.WARNING, repair.getSeverity());
        assertEquals(flow.answer(), repair.getAffectedPath());
        assertEquals(List.of(repair), flow.attempt().getIssues());
        assertEquals(List.of(flow.answer()), flow.attempt().getSubmittedFiles());
        assertTrue(flow.attempt().isValid());
    }

    @Test
    void inlineBlockHeadersAndBomCrlfSurviveTheWholeWorkflowUnchanged() throws IOException {
        String answer = "\uFEFF/* nAmE: " + STUDENT.getName() + " */\r\n/* EMAIL ID: "
                + STUDENT.getEmail() + " */\r\npublic class Q1 {}\r\n";
        String templateCompanion = "\uFEFF/* Name: */\r\n/* Email ID: */\r\nclass Companion {}\r\n";
        String companion = "\uFEFFclass Companion {}\r\n";
        Flow flow = runWorkflow(answer, companion, templateCompanion, List.of());

        assertEquals(List.of("Q1.java", "Companion.java"), flow.question().getEditableFiles());
        assertArrayEquals(answer.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(flow.answer()));
        assertArrayEquals(companion.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(flow.companion()));
        assertTrue(flow.submission().getSubmissionIssues().isEmpty(),
                flow.submission().getSubmissionIssues().toString());
        assertTrue(flow.attempt().isValid());
    }

    private Flow runWorkflow(String answer, String companion, String templateCompanion,
            List<SubmissionIssue> earlierIssues) throws IOException {
        Path template = temp.resolve("templates");
        write(template.resolve("Q1/Q1.java"), BLANK_HEADER + "public class Q1 {}\n");
        write(template.resolve("Q1/Companion.java"), templateCompanion);
        AssessmentDefinition configured = new AssessmentDefinition("templates", List.of(
                new QuestionDefinition("Q1", "Q1", template,
                        List.of("Q1.java"), List.of("Companion.java"))));
        // Matching the username avoids unrelated root-renaming warnings in these header-focused cases.
        Path extractedRoot = temp.resolve(STUDENT.getUsername());
        write(extractedRoot.resolve("Q1/Q1.java"), answer);
        write(extractedRoot.resolve("Q1/Companion.java"), companion);
        StudentSubmission extracted = new StudentSubmission(STUDENT,
                temp.resolve("example.student.zip"), extractedRoot);
        extracted.addIssues(earlierIssues);

        StudentSubmission normalized = SubmissionNormalizer.normalize(extracted, configured);
        AssessmentDefinition detected = EditableFileDetector.detect(configured, List.of(normalized));
        SubmissionFileFinder finder = new SubmissionFileFinder();
        SubmissionValidator validator = new SubmissionValidator(List.of(new QuestionFolderRule(),
                new RequiredFileRule(finder), new FileNamingRule(finder), new StudentHeaderRule(finder)),
                List.of(new UnexpectedFileRule()), finder);
        validator.validate(normalized, detected);
        return new Flow(normalized, detected.getQuestions().getFirst());
    }

    private static String longComment(String name, String email) {
        return "/*\n" + " * Assessment instructions\n".repeat(25)
                + " * Name: " + name + "\n * Email ID: " + email + "\n */\n";
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private record Flow(StudentSubmission submission, QuestionDefinition question) {
        Path answer() {
            return submission.getRootDirectory().resolve("Q1/Q1.java");
        }

        Path companion() {
            return submission.getRootDirectory().resolve("Q1/Companion.java");
        }

        QuestionAttempt attempt() {
            return submission.getQuestionAttempts().getFirst();
        }
    }
}
