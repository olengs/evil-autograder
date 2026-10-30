import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import submission.StudentCsvReader;
import submission.StudentIdentifier;
import submission.SubmissionPreparer;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.FileNamingRule;
import validation.rules.QuestionFolderRule;
import validation.rules.RequiredFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

/** Duplicate inputs remain inspectable, but cannot silently select a person or a winning ZIP. */
class DuplicateSubmissionTest {
    private static final StudentDetails STUDENT = new StudentDetails(
            "Example Student", "example.student", "example.student@school.edu");
    private static final StudentDetails OTHER = new StudentDetails(
            "Other Student", "other.student", "other.student@school.edu");

    @TempDir
    Path temp;
    private Path zips;
    private AssessmentDefinition assessment;

    @BeforeEach
    void createAssessment() throws IOException {
        zips = Files.createDirectories(temp.resolve("zips"));
        Path resources = Files.createDirectories(temp.resolve("templates"));
        for (String question : List.of("Q1", "Q2")) {
            Path file = resources.resolve(question).resolve(question + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "// Name:\n// Email ID:\npublic class " + question + " { }\n");
        }
        assessment = new AssessmentDefinition("templates", List.of(
                new QuestionDefinition("Q1", "Q1", resources, List.of("Q1.java"), List.of()),
                new QuestionDefinition("Q2", "Q2", resources, List.of("Q2.java"), List.of())));
    }

    @Test
    void csvReaderRetainsAndLogsCanonicalUsernameAndEmailDuplicates() throws IOException {
        Path csv = temp.resolve("scoresheet.csv");
        Path log = temp.resolve("csv.log.jsonl");
        Files.writeString(csv, "id,username,last,first,email\n"
                + "1,example.student,Student,Example Student,example.student@school.edu\n"
                + "2,EXAMPLE.STUDENT,Student,Conflicting Person,EXAMPLE.STUDENT@SCHOOL.EDU\n"
                + "3,other.student,Student,Other Student,other.student@school.edu\n");

        List<StudentDetails> students = new StudentCsvReader().read(csv, log);

        assertEquals(3, students.size(), "Duplicate records must remain visible to identification");
        List<JsonObject> records = Files.readAllLines(log).stream()
                .map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
        assertEquals(2, records.size());
        for (String issue : List.of("duplicate roster username", "duplicate roster email")) {
            JsonObject record = records.stream().filter(row -> row.get("issue").getAsString().equals(issue))
                    .findFirst().orElseThrow();
            assertEquals("ambiguous", record.get("status").getAsString());
            assertEquals(2, record.get("firstrow").getAsLong());
            assertEquals(3, record.get("row").getAsLong());
        }
        StudentIdentifier identifier = new StudentIdentifier();
        assertEquals(2, identifier.findMatches(Path.of("example.student.zip"), students).size());
        assertNull(identifier.identify(Path.of("example.student.zip"), students));
        assertEquals(OTHER.getUsername(), identifier.identify(Path.of("other.student.zip"), students).getUsername());
    }

    @Test
    void csvReaderLogsSameUsernameAcrossDifferentDomainsWithoutDroppingRows() throws IOException {
        Path csv = temp.resolve("different-domains.csv");
        Path log = temp.resolve("different-domains.jsonl");
        Files.writeString(csv, "id,username,last,first,email\n"
                + "1,example.student,Student,Example Student,example.student@school.edu\n"
                + "2,example.student,Student,Another Person,example.student@other.edu\n");

        List<StudentDetails> students = new StudentCsvReader().read(csv, log);

        assertEquals(2, students.size());
        assertEquals(1, Files.readAllLines(log).size());
        assertTrue(Files.readString(log).contains("duplicate roster username"));
        assertNull(new StudentIdentifier().identify(Path.of("example.student.zip"), students));
    }

    @Test
    void identicalRosterRowsBlockAmbiguousSubmissionWithoutRedundantUnknownIssue() throws IOException {
        writeZip("example.student.zip", STUDENT, "original");

        StudentSubmission submission = prepareAndValidate(List.of(STUDENT, STUDENT)).getFirst();

        assertNull(submission.getStudent());
        SubmissionIssue issue = issue(submission, "ambiguous student");
        assertGlobalPreparationError(issue);
        assertFalse(submission.getSubmissionIssues().stream().anyMatch(record -> record.getCode().equals("unknown student")));
        assertEquals(1, submission.getSubmissionIssues().stream()
                .filter(record -> record.getCode().equals("ambiguous student")).count());
        assertBlockedBy(submission, issue);
        assertEquals(source(STUDENT, "Q1", "original"), Files.readString(submission.getRootDirectory().resolve("Q1/Q1.java")));
    }

    @Test
    void conflictingRosterIdentitiesAreNeverSelectedAndDoNotBlockUnrelatedStudent() throws IOException {
        StudentDetails conflict = new StudentDetails("Different Person", "EXAMPLE.STUDENT", "EXAMPLE.STUDENT@other.edu");
        writeZip("2025-2026-EXAMPLE.STUDENT.zip", STUDENT, "ambiguous");
        writeZip("other.student.zip", OTHER, "healthy");

        List<StudentSubmission> submissions = prepareAndValidate(List.of(STUDENT, conflict, OTHER));

        StudentSubmission ambiguous = forZip(submissions, "2025-2026-EXAMPLE.STUDENT.zip");
        assertNull(ambiguous.getStudent());
        assertBlockedBy(ambiguous, issue(ambiguous, "ambiguous student"));
        StudentSubmission healthy = forZip(submissions, "other.student.zip");
        assertSame(OTHER, healthy.getStudent());
        assertTrue(healthy.getSubmissionIssues().stream().noneMatch(record -> record.getSeverity() == IssueSeverity.ERROR));
        assertTrue(healthy.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));
    }

    @Test
    void caseAndAcademicYearAliasesPreserveAllZipsAndBlockEveryDuplicate() throws IOException {
        Path firstZip = writeZip("2025-2026-EXAMPLE.STUDENT.ZIP", STUDENT, "first");
        Path secondZip = writeZip("example.student.zip", STUDENT, "second");
        byte[] firstBytes = Files.readAllBytes(firstZip);
        byte[] secondBytes = Files.readAllBytes(secondZip);
        writeZip("other.student.zip", OTHER, "healthy");

        List<StudentSubmission> submissions = prepareAndValidate(List.of(STUDENT, OTHER));

        StudentSubmission first = forZip(submissions, firstZip.getFileName().toString());
        StudentSubmission second = forZip(submissions, secondZip.getFileName().toString());
        assertNotEquals(first.getRootDirectory(), second.getRootDirectory());
        assertEquals(source(STUDENT, "Q1", "first"), Files.readString(first.getRootDirectory().resolve("Q1/Q1.java")));
        assertEquals(source(STUDENT, "Q1", "second"), Files.readString(second.getRootDirectory().resolve("Q1/Q1.java")));
        assertArrayEquals(firstBytes, Files.readAllBytes(firstZip));
        assertArrayEquals(secondBytes, Files.readAllBytes(secondZip));
        for (StudentSubmission duplicate : List.of(first, second)) {
            SubmissionIssue error = issue(duplicate, "duplicate submission");
            assertGlobalPreparationError(error);
            assertEquals(duplicate.getOriginalZip(), error.getAffectedPath());
            assertTrue(error.getMessage().contains(firstZip.getFileName().toString()));
            assertTrue(error.getMessage().contains(secondZip.getFileName().toString()));
            assertTrue(error.getMessage().contains("select one ZIP"));
            assertBlockedBy(duplicate, error);
        }
        StudentSubmission healthy = forZip(submissions, "other.student.zip");
        assertTrue(healthy.getSubmissionIssues().stream().noneMatch(record -> record.getSeverity() == IssueSeverity.ERROR));
        assertTrue(healthy.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));
    }

    @Test
    void unreadableDuplicateStillBlocksItsOtherCopyWithoutDiscardingPreparationError() throws IOException {
        Files.writeString(zips.resolve("2024-2025-example.student.zip"), "not a ZIP");
        writeZip("example.student.zip", STUDENT, "valid");

        List<StudentSubmission> submissions = prepareAndValidate(List.of(STUDENT));

        assertEquals(2, submissions.size());
        for (StudentSubmission submission : submissions) {
            assertBlockedBy(submission, issue(submission, "duplicate submission"));
        }
        StudentSubmission corrupt = forZip(submissions, "2024-2025-example.student.zip");
        assertGlobalPreparationError(issue(corrupt, "submission preparation error"));
        assertTrue(Files.exists(corrupt.getOriginalZip()));
        assertEquals(source(STUDENT, "Q1", "valid"), Files.readString(
                forZip(submissions, "example.student.zip").getRootDirectory().resolve("Q1/Q1.java")));
    }

    @Test
    void uniqueSubmissionStillUsesNormalPreparationAndValidationHandoff() throws IOException {
        writeZip("2025-2026-EXAMPLE.STUDENT.ZIP", STUDENT, "unique");

        StudentSubmission submission = prepareAndValidate(List.of(STUDENT, OTHER)).getFirst();

        assertSame(STUDENT, submission.getStudent());
        assertEquals(STUDENT.getUsername(), submission.getRootDirectory().getFileName().toString());
        assertTrue(submission.getSubmissionIssues().stream().noneMatch(record -> record.getSeverity() == IssueSeverity.ERROR));
        assertEquals(2, submission.getQuestionAttempts().size());
        assertTrue(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid));
    }

    @Test
    void ambiguousIdentityIssueSurvivesFailedZipExtraction() throws IOException {
        Files.writeString(zips.resolve("example.student.zip"), "not a ZIP");

        StudentSubmission submission = prepareAndValidate(List.of(STUDENT, STUDENT)).getFirst();

        assertGlobalPreparationError(issue(submission, "ambiguous student"));
        assertGlobalPreparationError(issue(submission, "submission preparation error"));
        assertBlockedBy(submission, issue(submission, "ambiguous student"));
    }

    private List<StudentSubmission> prepareAndValidate(List<StudentDetails> roster) throws IOException {
        List<StudentSubmission> submissions = SubmissionPreparer.prepare(zips, temp.resolve("workspace"), roster, assessment);
        SubmissionFileFinder finder = new SubmissionFileFinder();
        SubmissionValidator validator = new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(finder),
                new FileNamingRule(finder), new StudentHeaderRule(finder)), List.of(new UnexpectedFileRule()), finder);
        submissions.forEach(submission -> validator.validate(submission, assessment));
        return submissions;
    }

    private Path writeZip(String zipName, StudentDetails student, String marker) throws IOException {
        Path file = zips.resolve(zipName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String question : List.of("Q1", "Q2")) {
                zip.putNextEntry(new ZipEntry("RenameToYourUsername/" + question + "/" + question + ".java"));
                zip.write(source(student, question, marker).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    private static String source(StudentDetails student, String question, String marker) {
        return "// Name: " + student.getName() + "\n// Email ID: " + student.getEmail()
                + "\npublic class " + question + " { /* " + marker + " */ }\n";
    }

    private static StudentSubmission forZip(List<StudentSubmission> submissions, String fileName) {
        return submissions.stream().filter(submission -> submission.getOriginalZip().getFileName().toString().equals(fileName))
                .findFirst().orElseThrow();
    }

    private static SubmissionIssue issue(StudentSubmission submission, String code) {
        return submission.getSubmissionIssues().stream().filter(record -> record.getCode().equals(code)).findFirst().orElseThrow();
    }

    private static void assertGlobalPreparationError(SubmissionIssue issue) {
        assertEquals(IssueSeverity.ERROR, issue.getSeverity());
        assertEquals("preparation", issue.getStage());
        assertFalse(issue.isResolved());
        assertNull(issue.getQuestionId());
    }

    private static void assertBlockedBy(StudentSubmission submission, SubmissionIssue issue) {
        assertEquals(2, submission.getQuestionAttempts().size());
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            assertFalse(attempt.isValid());
            assertTrue(attempt.getIssues().contains(issue));
        }
    }
}
