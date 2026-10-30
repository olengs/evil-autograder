import static org.junit.jupiter.api.Assertions.*;

import config.AssessmentLoader;
import grading.AssessmentGrader;
import grading.QuestionGrader;
import grading.TesterLoader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import model.AssessmentDefinition;
import model.GradeStatus;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionGrade;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import submission.EditableFileDetector;
import submission.SubmissionPreparer;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.FileNamingRule;
import validation.rules.QuestionFolderRule;
import validation.rules.RequiredFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

/** Runs the submission/validation handoff through the existing grader without replacing it. */
class SubmissionGradingHandoffTest {
    private static final StudentDetails STUDENT = new StudentDetails(
            "Handoff Student", "handoff.student", "handoff.student@school.edu");
    private static final String BLANK_HEADER = "// Name:\n// Email ID:\n";

    @TempDir
    Path temp;
    private Path zips;
    private Path testers;
    private AssessmentDefinition assessment;

    @BeforeEach
    void createInputs() throws IOException {
        Path resources = temp.resolve("StartingFiles");
        write(resources.resolve("Q1/Q1.java"), BLANK_HEADER
                + "public class Q1 { public static int answer() { return -1; } }\n");
        write(resources.resolve("Q1/Work.java"), BLANK_HEADER
                + "public class Work { public static int answer() { return -100; } }\n");
        write(resources.resolve("Q1/Trusted.java"),
                "public class Trusted { public static int offset() { return 1; } }\n");
        write(resources.resolve("Q2/Q2.java"), BLANK_HEADER
                + "public class Q2 { public static int answer() { return -1; } }\n");
        assessment = AssessmentLoader.load(resources);
        zips = Files.createDirectories(temp.resolve("zips"));
        testers = Files.createDirectories(temp.resolve("testers"));
        writeTester("Q1", 42);
        writeTester("Q2", 7);
    }

    @Test
    @Timeout(30)
    void normalizedAnswerAndDetectedCompanionReachGradingWithIssuesIntact() throws IOException {
        writeZip(answerEntries(true));
        StudentSubmission submission = SubmissionPreparer.prepare(
                zips, temp.resolve("workspace"), List.of(STUDENT), assessment).getFirst();
        AssessmentDefinition detected = EditableFileDetector.detect(assessment, List.of(submission));
        validator().validate(submission, detected);

        QuestionAttempt first = attempt(submission, "Q1");
        Path root = submission.getRootDirectory();
        assertEquals(List.of("Q1.java", "Work.java"), first.getQuestionDefinition().getEditableFiles());
        assertEquals(List.of(root.resolve("Q1/Q1.java"), root.resolve("Q1/Work.java")), first.getSubmittedFiles());
        assertTrue(first.getSubmittedFiles().stream().allMatch(Files::isRegularFile));
        List<SubmissionIssue> issuesBeforeGrading = submission.getSubmissionIssues();
        assertTrue(issuesBeforeGrading.stream().anyMatch(issue -> issue.getCode().equals("student header normalized")));
        assertTrue(issuesBeforeGrading.stream().allMatch(issue -> issue.getSeverity() == IssueSeverity.WARNING
                && issue.getStage().equals("normalization") && issue.isResolved()), issuesBeforeGrading.toString());
        assertTrue(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));

        Map<StudentSubmission, List<QuestionGrade>> results = grader().grade(
                List.of(submission), detected, TesterLoader.load(testers));

        assertSame(submission, results.keySet().iterator().next());
        assertEquals(issuesBeforeGrading, submission.getSubmissionIssues());
        assertEquals(2, results.get(submission).size());
        for (QuestionGrade grade : results.get(submission)) {
            assertEquals(GradeStatus.COMPLETED, grade.getStatus(), grade.getMessage());
            assertEquals(1.0, grade.getEarnedMarks(), grade.getQuestionId());
        }
        //The template Work.answer() returns -100; passing proves the student's companion was used.
        assertEquals(1.0, grade(results.get(submission), "Q1").getEarnedMarks());
        assertTrue(Files.readString(root.resolve("Q1/Work.java")).contains("return 41;"));
        assertTrue(first.getIssues().stream().anyMatch(issue -> issue.getCode().equals("student header normalized")));
    }

    @Test
    @Timeout(30)
    void missingRequiredAnswerSkipsOnlyItsQuestionAndKeepsIssueHandoff() throws IOException {
        writeZip(answerEntries(false));
        StudentSubmission submission = SubmissionPreparer.prepare(
                zips, temp.resolve("workspace"), List.of(STUDENT), assessment).getFirst();
        AssessmentDefinition detected = EditableFileDetector.detect(assessment, List.of(submission));
        validator().validate(submission, detected);
        List<SubmissionIssue> issuesBeforeGrading = submission.getSubmissionIssues();
        SubmissionIssue missing = issuesBeforeGrading.stream()
                .filter(issue -> issue.getCode().equals("missing required file")).findFirst().orElseThrow();
        assertEquals("Q2", missing.getQuestionId());
        assertEquals("validation", missing.getStage());
        assertFalse(missing.isResolved());
        assertTrue(attempt(submission, "Q1").isValid());
        assertFalse(attempt(submission, "Q2").isValid());
        assertFalse(attempt(submission, "Q1").getIssues().contains(missing));
        assertTrue(attempt(submission, "Q2").getIssues().contains(missing));

        Map<StudentSubmission, List<QuestionGrade>> results = grader().grade(
                List.of(submission), detected, TesterLoader.load(testers));

        assertSame(submission, results.keySet().iterator().next());
        assertEquals(issuesBeforeGrading, submission.getSubmissionIssues());
        QuestionGrade first = grade(results.get(submission), "Q1");
        assertEquals(GradeStatus.COMPLETED, first.getStatus(), first.getMessage());
        assertEquals(1.0, first.getEarnedMarks());
        QuestionGrade second = grade(results.get(submission), "Q2");
        assertEquals(GradeStatus.SKIPPED, second.getStatus());
        assertEquals(0.0, second.getEarnedMarks());
        assertEquals(missing.getMessage(), second.getMessage());
    }

    private Map<String, String> answerEntries(boolean includeSecond) {
        Map<String, String> entries = new LinkedHashMap<>();
        //Wrong wrapper, question capitalization, filename capitalization and missing header are repairable.
        String prefix = "Downloads/StartingFiles/";
        entries.put(prefix + "q1/q1.java", "public class Q1 {\n"
                + "  public static int answer() { return Work.answer() + Trusted.offset(); }\n}\n");
        entries.put(prefix + "q1/Work.java", studentHeader()
                + "public class Work { public static int answer() { return 41; } }\n");
        if (includeSecond) {
            entries.put(prefix + "Q2/Q2.java", studentHeader()
                    + "public class Q2 { public static int answer() { return 7; } }\n");
        } else {
            entries.put(prefix + "Q2/", "");
        }
        return entries;
    }

    private void writeZip(Map<String, String> entries) throws IOException {
        try (OutputStream output = Files.newOutputStream(zips.resolve(STUDENT.getUsername() + ".zip"));
                ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private void writeTester(String question, int expected) throws IOException {
        write(testers.resolve(question + "Tester.java"), "public class " + question + "Tester {\n"
                + "  public static void main(String[] args) {\n"
                + "    System.out.println(\"Test 1: student answer and companion\");\n"
                + "    System.out.println(" + question + ".answer() == " + expected + " ? \"Passed\" : \"Failed\");\n"
                + "  }\n}\n");
    }

    private static SubmissionValidator validator() {
        SubmissionFileFinder finder = new SubmissionFileFinder();
        return new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(finder),
                new FileNamingRule(finder), new StudentHeaderRule(finder)), List.of(new UnexpectedFileRule()), finder);
    }

    private static AssessmentGrader grader() {
        return new AssessmentGrader(new QuestionGrader(Duration.ofSeconds(20), Duration.ofSeconds(5)));
    }

    private static QuestionAttempt attempt(StudentSubmission submission, String id) {
        return submission.getQuestionAttempts().stream()
                .filter(attempt -> attempt.getQuestionDefinition().getId().equals(id)).findFirst().orElseThrow();
    }

    private static QuestionGrade grade(List<QuestionGrade> grades, String id) {
        return grades.stream().filter(grade -> grade.getQuestionId().equals(id)).findFirst().orElseThrow();
    }

    private static String studentHeader() {
        return "// Name: " + STUDENT.getName() + "\n// Email ID: " + STUDENT.getEmail() + "\n";
    }

    private static void write(Path file, String contents) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
