import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import submission.EditableFileDetector;
import submission.SubmissionNormalizer;
import submission.SubmissionPreparer;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.FileNamingRule;
import validation.rules.QuestionFolderRule;
import validation.rules.RequiredFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

/** Exercises the issue handoff using real extracted folders and ZIP files. */
class SubmissionIssueFlowTest {
    private static final StudentDetails STUDENT = new StudentDetails(
            "Example Student", "example.student", "example.student@school.edu");
    private static final List<String> ANSWERS = List.of("Q1/Q1a.java", "Q1/Q1b.java", "Q2/Q2.java");

    @TempDir
    Path temp;
    private AssessmentDefinition assessment;

    @BeforeEach
    void createAssessment() throws IOException {
        Path resources = temp.resolve("templates");
        for (String answer : ANSWERS) {
            write(resources.resolve(answer), source(answer));
        }
        write(resources.resolve("Q1/Helper.java"), "class Helper { }\n");
        assessment = new AssessmentDefinition("templates", List.of(
                new QuestionDefinition("Q1a", "Q1", resources, List.of("Q1a.java"), List.of("Helper.java")),
                new QuestionDefinition("Q1b", "Q1", resources, List.of("Q1b.java"), List.of("Helper.java")),
                new QuestionDefinition("Q2", "Q2", resources, List.of("Q2.java"), List.of())));
    }

    @Test
    void inheritedQuestionIssueReachesOnlyItsAttemptWithoutDuplicateRecords() throws IOException {
        StudentSubmission submission = completeSubmission("student");
        SubmissionIssue upstream = new SubmissionIssue("ambiguous answer file", IssueSeverity.ERROR,
                "Two candidate answers remain", "Q1a", submission.getRootDirectory().resolve("Q1/Q1a.java"),
                "normalization", false);
        submission.addIssues(List.of(upstream));
        write(submission.getRootDirectory().resolve("Q2/Q2.java"), "// Name: Other Student\n"
                + "// Email ID: other@school.edu\npublic class Q2 { }\n");

        validator().validate(submission, assessment);

        assertTrue(attempt(submission, "Q1a").getIssues().stream().anyMatch(issue -> issue == upstream));
        assertFalse(attempt(submission, "Q1a").isValid());
        assertTrue(attempt(submission, "Q1b").isValid());
        assertTrue(attempt(submission, "Q2").isValid(), "Header warnings must not block another question");
        assertFalse(attempt(submission, "Q1b").getIssues().contains(upstream));
        assertFalse(attempt(submission, "Q2").getIssues().contains(upstream));
        assertEquals(1, submission.getSubmissionIssues().stream().filter(issue -> issue == upstream).count());
        List<SubmissionIssue> headerIssues = issuesWithCode(submission, "mismatched student header");
        assertEquals(2, headerIssues.size());
        for (SubmissionIssue issue : headerIssues) {
            assertEquals("validation", issue.getStage());
            assertFalse(issue.isResolved());
            assertTrue(attempt(submission, "Q2").getIssues().contains(issue));
            assertFalse(attempt(submission, "Q1a").getIssues().contains(issue));
            assertEquals(1, submission.getSubmissionIssues().stream().filter(record -> record == issue).count());
        }
    }

    @Test
    void submissionWidePreparationErrorReachesEveryAttemptOnce() throws IOException {
        StudentSubmission submission = completeSubmission("student");
        SubmissionIssue upstream = new SubmissionIssue("unknown student", IssueSeverity.ERROR,
                "Student could not be identified", null, submission.getOriginalZip(), "preparation", false);
        submission.addIssues(List.of(upstream));

        validator().validate(submission, assessment);

        assertEquals(1, submission.getSubmissionIssues().size());
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            assertFalse(attempt.isValid());
            assertEquals(List.of(upstream), attempt.getIssues());
        }
    }

    @Test
    void resolvedNormalizationCorrectionsKeepCurrentPathsAfterRootMove() throws IOException {
        Path extracted = temp.resolve("extracted");
        for (String answer : ANSWERS) {
            write(extracted.resolve(answer), source(answer));
        }
        renameCase(extracted.resolve("Q1/Q1a.java"), "q1A.java");
        Files.move(extracted.resolve("Q1"), extracted.resolve("question one"));
        write(extracted.resolve("Q2/Q2.java"), "public class Q2 { }\n");
        Path zip = temp.resolve("example.student.zip");
        StudentSubmission original = new StudentSubmission(STUDENT, zip, extracted);
        SubmissionIssue earlier = new SubmissionIssue("archive note", IssueSeverity.WARNING,
                "Original ZIP retained", null, zip, "preparation", false);
        original.addIssues(List.of(earlier));

        StudentSubmission normalized = SubmissionNormalizer.normalize(original, assessment);
        validator().validate(normalized, assessment);

        assertTrue(normalized.getSubmissionIssues().contains(earlier));
        assertEquals(1, normalized.getSubmissionIssues().stream().filter(issue -> issue == earlier).count());
        for (String code : List.of("question folder normalized", "filename normalized",
                "submission root normalized", "student header normalized")) {
            assertFalse(issuesWithCode(normalized, code).isEmpty(), code);
        }
        for (SubmissionIssue issue : normalized.getSubmissionIssues()) {
            if (!issue.getStage().equals("normalization")) continue;
            assertTrue(issue.isResolved(), issue.toString());
            assertEquals(IssueSeverity.WARNING, issue.getSeverity());
            assertTrue(issue.getAffectedPath().startsWith(normalized.getRootDirectory()), issue.toString());
            assertTrue(Files.exists(issue.getAffectedPath()), issue.getAffectedPath().toString());
        }
        for (QuestionAttempt attempt : normalized.getQuestionAttempts()) {
            assertTrue(attempt.isValid(), attempt.getIssues().toString());
        }
    }

    @Test
    void ambiguousNormalizationRemainsUnresolvedAndBlocksAttempts() throws IOException {
        Path extracted = temp.resolve("ambiguous");
        for (String folder : List.of("first", "second")) {
            for (String answer : ANSWERS) {
                write(extracted.resolve(folder).resolve(answer), source(answer));
            }
        }

        StudentSubmission normalized = SubmissionNormalizer.normalize(
                new StudentSubmission(STUDENT, temp.resolve("example.student.zip"), extracted), assessment);
        validator().validate(normalized, assessment);

        SubmissionIssue issue = issuesWithCode(normalized, "normalization error").getFirst();
        assertEquals("normalization", issue.getStage());
        assertFalse(issue.isResolved());
        assertEquals(IssueSeverity.ERROR, issue.getSeverity());
        for (QuestionAttempt attempt : normalized.getQuestionAttempts()) {
            assertFalse(attempt.isValid());
            assertTrue(attempt.getIssues().contains(issue));
        }
    }

    @Test
    void corruptedZipIsRecordedAndDoesNotInterruptNextSubmission() throws IOException {
        Path zips = Files.createDirectories(temp.resolve("zips"));
        Files.writeString(zips.resolve("a.corrupt.zip"), "invalid ZIP");
        writeZip(zips.resolve("example.student.zip"), Map.of(
                "example.student/Q1/Q1a.java", source("Q1a.java"),
                "example.student/Q1/Q1b.java", source("Q1b.java"),
                "example.student/Q2/Q2.java", source("Q2.java")));

        List<StudentSubmission> submissions = SubmissionPreparer.prepare(
                zips, temp.resolve("workspace"), List.of(STUDENT), assessment);
        assertEquals(2, submissions.size());
        for (StudentSubmission submission : submissions) validator().validate(submission, assessment);

        StudentSubmission corrupt = submissions.getFirst();
        SubmissionIssue error = issuesWithCode(corrupt, "submission preparation error").getFirst();
        assertEquals("preparation", error.getStage());
        assertFalse(error.isResolved());
        assertEquals(IssueSeverity.ERROR, error.getSeverity());
        for (QuestionAttempt attempt : corrupt.getQuestionAttempts()) {
            assertFalse(attempt.isValid());
            assertTrue(attempt.getIssues().contains(error));
        }
        StudentSubmission good = submissions.getLast();
        assertTrue(good.getSubmissionIssues().isEmpty(), good.getSubmissionIssues().toString());
        assertTrue(good.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));
    }

    @Test
    void unknownStudentIssueUsesPreparationStageAndReachesAllQuestions() throws IOException {
        Path zips = Files.createDirectories(temp.resolve("unknown-zips"));
        writeZip(zips.resolve("unlisted.person.zip"), Map.of(
                "Q1/Q1a.java", source("Q1a.java"),
                "Q1/Q1b.java", source("Q1b.java"),
                "Q2/Q2.java", source("Q2.java")));

        StudentSubmission submission = SubmissionPreparer.prepare(
                zips, temp.resolve("workspace"), List.of(STUDENT), assessment).getFirst();
        validator().validate(submission, assessment);

        SubmissionIssue issue = issuesWithCode(submission, "unknown student").stream()
                .filter(record -> record.getSeverity() == IssueSeverity.ERROR).findFirst().orElseThrow();
        assertEquals("preparation", issue.getStage());
        assertFalse(issue.isResolved());
        assertNull(issue.getQuestionId());
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            assertFalse(attempt.isValid());
            assertTrue(attempt.getIssues().contains(issue));
        }
    }

    @Test
    void repairedBomHeaderDoesNotProduceFalseValidationWarnings() throws IOException {
        StudentSubmission original = completeSubmission("bom-extracted");
        write(original.getRootDirectory().resolve("Q1/Q1a.java"), "\uFEFFpublic class Q1a { }\r\n");

        StudentSubmission normalized = SubmissionNormalizer.normalize(original, assessment);
        validator().validate(normalized, assessment);

        assertEquals(1, issuesWithCode(normalized, "student header normalized").size());
        assertTrue(issuesWithCode(normalized, "missing student header").isEmpty());
        assertTrue(issuesWithCode(normalized, "mismatched student header").isEmpty());
        assertTrue(Files.readString(normalized.getRootDirectory().resolve("Q1/Q1a.java")).startsWith("\uFEFF"));
    }

    @Test
    void headerValidationChecksRequiredAnswerAndKeepsEditableCompanionSelected() throws IOException {
        Path resources = temp.resolve("companion-templates");
        write(resources.resolve("Q3/Q3.java"), source("Q3.java"));
        write(resources.resolve("Q3/ShapeComparator.java"), "// Name:\n// Email ID:\nclass ShapeComparator { }\n");
        AssessmentDefinition configured = new AssessmentDefinition("companion-templates", List.of(
                new QuestionDefinition("Q3", "Q3", resources, List.of("Q3.java"), List.of("ShapeComparator.java"))));
        Path root = temp.resolve("companion-student");
        Path requiredAnswer = root.resolve("Q3/Q3.java");
        write(requiredAnswer, "// Name: Other Person\n// Email ID: other@school.edu\npublic class Q3 { }\n");
        Path companion = root.resolve("Q3/ShapeComparator.java");
        write(companion, "// Name: Other Person\n// Email ID: other@school.edu\nclass ShapeComparator { }\n");
        StudentSubmission submission = new StudentSubmission(STUDENT, temp.resolve("student.zip"), root);
        AssessmentDefinition detected = EditableFileDetector.detect(configured, List.of(submission));

        validator().validate(submission, detected);

        assertTrue(detected.getQuestions().getFirst().getEditableFiles().contains("ShapeComparator.java"));
        assertTrue(attempt(submission, "Q3").getSubmittedFiles().contains(companion.toAbsolutePath().normalize()));
        assertTrue(attempt(submission, "Q3").getSubmittedFiles().contains(requiredAnswer.toAbsolutePath().normalize()));
        List<SubmissionIssue> issues = issuesWithCode(submission, "mismatched student header");
        assertEquals(2, issues.size());
        assertTrue(issues.stream().allMatch(issue -> issue.getAffectedPath().equals(requiredAnswer)),
                "Only required answers receive header warnings, even when an editable companion has mismatched details");
        assertTrue(issuesWithCode(submission, "missing student header").isEmpty());
        assertTrue(issues.stream().allMatch(issue -> issue.getStage().equals("validation") && !issue.isResolved()));
        assertTrue(attempt(submission, "Q3").getIssues().containsAll(issues));
    }

    @Test
    void wrongFilenameStillReceivesHeaderChecks() throws IOException {
        StudentSubmission submission = completeSubmission("wrong-case");
        Path folder = submission.getRootDirectory().resolve("Q1");
        renameCase(folder.resolve("Q1a.java"), "q1A.java");
        write(folder.resolve("q1A.java"), "public class Q1a { }\n");

        validator().validate(submission, assessment);

        assertEquals(1, issuesWithCode(submission, "wrong file name").size());
        assertEquals(2, issuesWithCode(submission, "missing student header").size());
        assertFalse(attempt(submission, "Q1a").isValid());
        assertTrue(attempt(submission, "Q1b").isValid());
    }

    @Test
    void extraFilesWarnOnlyAffectedQuestionsAndValidationDoesNotRestoreFiles() throws IOException {
        StudentSubmission submission = completeSubmission("extra-files");
        Path root = submission.getRootDirectory();
        Path helper = root.resolve("Q1/Helper.java");
        Path extra = root.resolve("Q1/Extra.java");
        write(helper, "class Helper { /* student changed this */ }\n");
        write(extra, "class Extra { }\n");
        byte[] helperBefore = Files.readAllBytes(helper);
        byte[] extraBefore = Files.readAllBytes(extra);

        validator().validate(submission, assessment);

        List<SubmissionIssue> issues = issuesWithCode(submission, "unexpected file");
        assertFalse(issues.isEmpty());
        assertEquals(List.of("Q1a", "Q1b"), issues.stream().map(SubmissionIssue::getQuestionId).sorted().toList());
        assertTrue(issues.stream().allMatch(issue -> issue.getSeverity() == IssueSeverity.WARNING));
        assertTrue(attempt(submission, "Q1a").getIssues().stream().anyMatch(issue -> issue.getCode().equals("unexpected file")));
        assertTrue(attempt(submission, "Q1b").getIssues().stream().anyMatch(issue -> issue.getCode().equals("unexpected file")));
        assertTrue(attempt(submission, "Q2").getIssues().isEmpty());
        assertTrue(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));
        assertArrayEquals(helperBefore, Files.readAllBytes(helper));
        assertArrayEquals(extraBefore, Files.readAllBytes(extra));
        assertEquals("class Helper { }\n", Files.readString(temp.resolve("templates/Q1/Helper.java")));
    }

    private StudentSubmission completeSubmission(String folder) throws IOException {
        Path root = temp.resolve(folder);
        for (String answer : ANSWERS) write(root.resolve(answer), source(answer));
        write(root.resolve("Q1/Helper.java"), "class Helper { }\n");
        return new StudentSubmission(STUDENT, temp.resolve("example.student.zip"), root);
    }

    private SubmissionValidator validator() {
        SubmissionFileFinder finder = new SubmissionFileFinder();
        return new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(finder),
                new FileNamingRule(finder), new StudentHeaderRule(finder)), List.of(new UnexpectedFileRule()), finder);
    }

    private static QuestionAttempt attempt(StudentSubmission submission, String questionId) {
        return submission.getQuestionAttempts().stream()
                .filter(candidate -> candidate.getQuestionDefinition().getId().equals(questionId)).findFirst().orElseThrow();
    }

    private static List<SubmissionIssue> issuesWithCode(StudentSubmission submission, String code) {
        return submission.getSubmissionIssues().stream().filter(issue -> issue.getCode().equals(code)).toList();
    }

    private static String source(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        String className = file.substring(0, file.length() - ".java".length());
        return "// Name: " + STUDENT.getName() + "\n// Email ID: " + STUDENT.getEmail()
                + "\npublic class " + className + " { }\n";
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void renameCase(Path file, String name) throws IOException {
        Path intermediate = file.resolveSibling("case-change.tmp");
        Files.move(file, intermediate);
        Files.move(intermediate, file.resolveSibling(name));
    }

    private static void writeZip(Path file, Map<String, String> entries) throws IOException {
        try (OutputStream output = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
