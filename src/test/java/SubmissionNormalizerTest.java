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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import submission.SubmissionPreparer;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.*;

//every case builds a real zip & runs the real loader + validator > zero mocks
class SubmissionNormalizerTest {
    private static final String USERNAME = "jiagu.wen.2025";
    private static final String NAME = "JIAGU WEN";
    private static final String EMAIL = "jiagu.wen.2025@computing.smu.edu.sg";
    private static final List<String> ANSWER_FILES = List.of("Q1/Q1a.java", "Q1/Q1b.java", "Q2/Q2.java", "Q3/Q3.java");

    @TempDir
    Path temp;
    private Path zips;
    private Path workspace;
    private AssessmentDefinition assessment;
    private List<StudentDetails> roster;

    @BeforeEach
    void createAssessment() throws IOException {
        zips = Files.createDirectories(temp.resolve("student-submission"));
        workspace = temp.resolve("workspace");
        Path resources = temp.resolve("RenameToYourUsername");
        for (String file : ANSWER_FILES) {
            write(resources.resolve(file), "public class " + className(file) + " { }");
        }
        write(resources.resolve("Q1/Shape.java"), "class Shape { }");
        write(resources.resolve("Q1/data.txt"), "data");
        assessment = AssessmentLoader.load(resources);
        roster = List.of(new StudentDetails(NAME, USERNAME, EMAIL));
    }

    //#region folder arrangement from the brief

    @Test
    void correctSubmissionIsLeftAloneWithoutNoise() throws IOException {
        StudentSubmission submission = load(USERNAME + ".zip", answers(USERNAME + "/"));

        assertWorkingStructure(submission);
        assertEquals(List.of(), submission.getSubmissionIssues());
    }

    @Test
    void forgottenRenameOfResourceFolder() throws IOException {
        StudentSubmission submission = load(USERNAME + ".zip", answers("RenameToYourUsername/"));

        assertWorkingStructure(submission);
        assertHasIssue(submission, "submission root normalized");
    }

    @Test
    void questionFoldersDirectlyInZipWithoutUsernameFolder() throws IOException {
        StudentSubmission submission = load(USERNAME + ".zip", answers(""));

        assertWorkingStructure(submission);
        assertHasIssue(submission, "submission root normalized");
    }

    @Test
    void academicYearPrefixOnZipNameStillIdentifiesStudent() throws IOException {
        assertWorkingStructure(load("2025-2026-" + USERNAME + ".zip", answers(USERNAME + "/")));
    }

    @Test
    void usernameFolderWithWrongCapitalsIsCorrected() throws IOException {
        assertWorkingStructure(load(USERNAME + ".zip", answers("JIAGU.WEN.2025/")));
    }

    //#endregion

    //#region improper hierarchy

    @Test
    void doubleWrappedUsernameFolder() throws IOException {
        assertWorkingStructure(load(USERNAME + ".zip", answers(USERNAME + "/" + USERNAME + "/")));
    }

    @Test
    void deeplyWrappedFolder() throws IOException {
        assertWorkingStructure(load(USERNAME + ".zip", answers("Downloads/exam/final/" + USERNAME + "/")));
    }

    @Test
    void strayFileBesideWrapperDoesNotHideAnswers() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put("readme.txt", "my notes");

        assertWorkingStructure(load(USERNAME + ".zip", entries));
    }

    @Test
    void looseAnswerFilesAtZipTopLevel() throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (String file : ANSWER_FILES) {
            entries.put(className(file) + ".java", source(className(file)));
        }

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertHasIssue(submission, "answer file relocated");
    }

    @Test
    void looseAnswerFilesInsideUsernameFolder() throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (String file : ANSWER_FILES) {
            entries.put(USERNAME + "/" + className(file) + ".java", source(className(file)));
        }

        assertWorkingStructure(load(USERNAME + ".zip", entries));
    }

    @Test
    void answerSavedInWrongQuestionFolderMovesToItsOwnFolder() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q2/Q2.java");
        entries.put(USERNAME + "/Q1/Q2.java", source("Q2"));

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertFalse(Files.exists(submission.getRootDirectory().resolve("Q1/Q2.java")));
    }

    @Test
    void questionFolderNestedInsideItself() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q3/Q3.java");
        entries.put(USERNAME + "/Q3/Q3/Q3.java", source("Q3"));

        assertWorkingStructure(load(USERNAME + ".zip", entries));
    }

    @Test
    void questionsScatteredAcrossFoldersAreReunited() throws IOException {
        Map<String, Object> entries = answers("part2/");
        entries.remove("part2/Q1/Q1a.java");
        entries.remove("part2/Q1/Q1b.java");
        entries.put("part1/Q1/Q1a.java", source("Q1a"));
        entries.put("part1/Q1/Q1b.java", source("Q1b"));

        assertWorkingStructure(load(USERNAME + ".zip", entries));
    }

    //#endregion

    //#region incorrect names

    @Test
    void questionFoldersWithWrongCapitals() throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (String file : ANSWER_FILES) {
            entries.put(USERNAME + "/" + file.toLowerCase().replace(".java", "").replaceAll("/.*", "/")
                    + className(file) + ".java", source(className(file)));
        }

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertHasIssue(submission, "question folder normalized");
    }

    @Test
    void misnamedQuestionFolderRecognisedFromItsAnswerFile() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q2/Q2.java");
        entries.put(USERNAME + "/Question 2/Q2.java", source("Q2"));
        entries.put(USERNAME + "/Question 2/Helper.java", "class Helper { }");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q2/Helper.java")),
                "helper classes must follow their folder");
    }

    @Test
    void answerFileWithWrongCapitals() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q1/Q1a.java");
        entries.put(USERNAME + "/Q1/q1A.java", source("Q1a"));

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertHasIssue(submission, "filename normalized");
    }

    @Test
    void supportFileWithWrongCapitalsIsCorrected() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/Q1/DATA.TXT", "data");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertTrue(fileNames(submission.getRootDirectory().resolve("Q1")).contains("data.txt"));
    }

    //#endregion

    //#region junk that must not change the result

    @Test
    void macOsArchiveJunkIsIgnored() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put("__MACOSX/" + USERNAME + "/Q1/._Q1a.java", "binary fork");
        entries.put(USERNAME + "/.DS_Store", "binary");
        entries.put(USERNAME + "/Q1/._Q1a.java", "binary fork");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertFalse(Files.exists(submission.getRootDirectory().resolve("__MACOSX")));
    }

    @Test
    void junkFolderContainingQuestionNamesIsNotTheAnswerRoot() throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put("__MACOSX/Q1/x", "x");
        entries.put("__MACOSX/Q2/x", "x");
        entries.put("__MACOSX/Q3/x", "x");
        entries.putAll(answers("work/"));

        assertWorkingStructure(load(USERNAME + ".zip", entries));
    }

    @Test
    void extraCopyOfAnAnswerDoesNotDisturbCorrectFolder() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/backup/Q1a.java", "public class Q1a { /* old attempt */ }");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertEquals(source("Q1a"), Files.readString(submission.getRootDirectory().resolve("Q1/Q1a.java")));
    }

    //#endregion

    //#region anomalies are reported & grading of the rest continues

    @Test
    void missingAnswerFileOnlyBlocksThatQuestion() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q1/Q1b.java");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission, "Q1b");
        assertHasIssue(submission, "missing required file");
    }

    @Test
    void missingQuestionFolderOnlyBlocksThatQuestion() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q2/Q2.java");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission, "Q2");
        assertHasIssue(submission, "missing question folder");
    }

    @Test
    void emptyQuestionFolderIsReportedAsMissingFile() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q2/Q2.java");
        entries.put(USERNAME + "/Q2/", null);

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission, "Q2");
        assertHasIssue(submission, "missing required file");
    }

    @Test
    void twoFoldersClaimingOneQuestionAreNotGuessedBetween() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q2/Q2.java");
        entries.put(USERNAME + "/attempt1/Q2.java", source("Q2"));
        entries.put(USERNAME + "/attempt2/Q2.java", source("Q2"));

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission, "Q2");
        assertHasIssue(submission, "normalization collision");
    }

    @Test
    void twoCopiesOfALooseAnswerAreNotGuessedBetween() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.remove(USERNAME + "/Q1/Q1a.java");
        entries.put(USERNAME + "/old/Q1a.java", source("Q1a"));
        entries.put(USERNAME + "/new/Q1a.java", source("Q1a"));

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission, "Q1a");
        assertHasIssue(submission, "ambiguous answer file");
    }

    @Test
    void twoCompleteAnswerFoldersAreNotGuessedBetween() throws IOException {
        Map<String, Object> entries = answers("final/");
        entries.putAll(answers("backup/"));

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission, "Q1a", "Q1b", "Q2", "Q3");
        assertHasIssue(submission, "normalization error");
    }

    @Test
    void usernameFolderWinsOverLeftoverResourceFolder() throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (String file : ANSWER_FILES) {
            entries.put("RenameToYourUsername/" + file, "public class " + className(file) + " { }");
        }
        entries.putAll(answers(USERNAME + "/"));

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertEquals(source("Q2"), Files.readString(submission.getRootDirectory().resolve("Q2/Q2.java")));
    }

    @Test
    void zipWithoutAnyAnswersReportsEveryQuestion() throws IOException {
        StudentSubmission submission = load(USERNAME + ".zip", Map.of("notes/essay.txt", "wrong upload"));

        assertBlockedQuestions(submission, "Q1a", "Q1b", "Q2", "Q3");
    }

    @Test
    void unknownStudentIsReportedButStructureIsStillArranged() throws IOException {
        StudentSubmission submission = load("someone.else.zip", answers("RenameToYourUsername/"));

        assertNull(submission.getStudent());
        assertHasIssue(submission, "unknown student");
        for (String file : ANSWER_FILES) {
            assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve(file)), file);
        }
    }

    //#endregion

    //#region broken zips must not stop the batch

    @Test
    void brokenZipsAreReportedAndLaterStudentsStillLoad() throws IOException {
        Files.write(zips.resolve("a.corrupt.zip"), "this is not a zip".getBytes(StandardCharsets.UTF_8));
        Files.write(zips.resolve("b.empty.bytes.zip"), new byte[0]);
        writeZip("c.no.entries.zip", Map.of());
        writeZip("d.unreadable.name.zip", Map.of("Q1/bad\u0000name.java", "x"));
        writeZip("e.escape.zip", Map.of("../../outside.java", "x"));
        writeZip(USERNAME + ".zip", answers(USERNAME + "/"));

        List<StudentSubmission> submissions = loadAll();

        assertEquals(6, submissions.size());
        for (StudentSubmission broken : submissions.subList(0, 5)) {
            assertFalse(errors(broken).isEmpty(), broken.getOriginalZip().toString());
        }
        assertWorkingStructure(submissions.getLast());
        assertFalse(Files.exists(temp.resolve("outside.java")));
    }

    @Test
    void zipEntryNamesNotInUtf8DoNotCrashTheBatch() throws IOException {
        Path zip = zips.resolve("a.bad.encoding.zip");
        writeZip("a.bad.encoding.zip", Map.of("Q1/café.java", "x"));
        byte[] bytes = Files.readAllBytes(zip);
        replaceUtf8NameWithLatin1(bytes);
        Files.write(zip, bytes);
        writeZip(USERNAME + ".zip", answers(USERNAME + "/"));

        List<StudentSubmission> submissions = loadAll();

        assertEquals(2, submissions.size());
        assertWorkingStructure(submissions.getLast());
    }

    @Test
    void twoZipsForOneStudentDoNotOverwriteEachOther() throws IOException {
        writeZip("2024-2025-" + USERNAME + ".zip", answers(USERNAME + "/"));
        writeZip(USERNAME + ".zip", answers("RenameToYourUsername/"));

        List<StudentSubmission> submissions = loadAll();

        assertNotEquals(submissions.getFirst().getRootDirectory(), submissions.getLast().getRootDirectory());
        submissions.forEach(this::assertWorkingStructure);
    }

    //#endregion

    //#region student headers

    @Test
    void missingHeaderIsFilledFromScoresheet() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/Q1/Q1a.java", "public class Q1a { }\n");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertWorkingStructure(submission);
        assertHasIssue(submission, "student header normalized");
        assertEquals("// Name: " + NAME + "\n// Email ID: " + EMAIL + "\npublic class Q1a { }\n",
                Files.readString(submission.getRootDirectory().resolve("Q1/Q1a.java")));
    }

    @Test
    void onlyTheMissingHeaderFieldIsAdded() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/Q2/Q2.java", "// Name: " + NAME + "\npublic class Q2 { }\n");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        String file = Files.readString(submission.getRootDirectory().resolve("Q2/Q2.java"));
        assertEquals(1, file.split("Name:", -1).length - 1, file);
        assertTrue(file.contains("// Email ID: " + EMAIL), file);
        assertNoHeaderProblems(submission);
    }

    @Test
    void blankHeaderFieldsAreFilled() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/Q2/Q2.java", "// Name:\n// Email ID:\npublic class Q2 { }\n");

        assertNoHeaderProblems(load(USERNAME + ".zip", entries));
    }

    @Test
    void blockCommentHeaderIsRecognised() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        String file = "/*\n * Name: " + NAME + "\n * Email: " + EMAIL + "\n */\npublic class Q2 { }\n";
        entries.put(USERNAME + "/Q2/Q2.java", file);

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertEquals(file, Files.readString(submission.getRootDirectory().resolve("Q2/Q2.java")));
        assertNoHeaderProblems(submission);
    }

    @Test
    void headerNamingAnotherStudentIsReportedNotOverwritten() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        String file = "// Name: SOMEONE ELSE\n// Email ID: someone@smu.edu.sg\npublic class Q2 { }\n";
        entries.put(USERNAME + "/Q2/Q2.java", file);

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertEquals(file, Files.readString(submission.getRootDirectory().resolve("Q2/Q2.java")));
        assertHasIssue(submission, "mismatched student header");
    }

    @Test
    void headerInsertionKeepsWindowsLineEndingsAndByteOrderMark() throws IOException {
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/Q2/Q2.java", "﻿public class Q2 {\r\n}\r\n");

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertEquals("﻿// Name: " + NAME + "\r\n// Email ID: " + EMAIL + "\r\npublic class Q2 {\r\n}\r\n",
                Files.readString(submission.getRootDirectory().resolve("Q2/Q2.java")));
    }

    @Test
    void sourceFileNotSavedAsUtf8KeepsItsBytesAndStillGetsAHeader() throws IOException {
        byte[] latin1 = "public class Q2 { /* café */ }\n".getBytes(StandardCharsets.ISO_8859_1);
        Map<String, Object> entries = answers(USERNAME + "/");
        entries.put(USERNAME + "/Q2/Q2.java", latin1);

        StudentSubmission submission = load(USERNAME + ".zip", entries);

        assertBlockedQuestions(submission);
        byte[] result = Files.readAllBytes(submission.getRootDirectory().resolve("Q2/Q2.java"));
        byte[] tail = java.util.Arrays.copyOfRange(result, result.length - latin1.length, result.length);
        assertArrayEquals(latin1, tail);
        assertNoHeaderProblems(submission);
    }

    //#endregion

    //#region the result must actually grade

    @Test
    void messySubmissionEarnsSameMarksAsCleanOne() throws IOException {
        Path testers = Files.createDirectories(temp.resolve("Tester-Files"));
        for (String file : ANSWER_FILES) {
            String answer = className(file);
            write(testers.resolve(answer + "Tester.java"), "public class " + answer + "Tester {\n"
                    + "  public static void main(String[] args) {\n"
                    + "    System.out.println(\"Test 1: answer\");\n"
                    + "    System.out.println(" + answer + ".answer() == 1 ? \"Passed\" : \"Failed\");\n"
                    + "  }\n}\n");
        }
        writeZip("clean.student.zip", answers("clean.student/"));

        Map<String, Object> messy = new LinkedHashMap<>();
        messy.put("__MACOSX/._junk", "junk");
        messy.put("Downloads/RenameToYourUsername/notes.txt", "notes");
        messy.put("Downloads/RenameToYourUsername/q1/q1A.java", "public class Q1a { public static int answer() { return 1; } }\n");
        messy.put("Downloads/RenameToYourUsername/q1/Q1b.java", source("Q1b"));
        messy.put("Downloads/RenameToYourUsername/Question 2/Q2.java", source("Q2"));
        messy.put("Downloads/RenameToYourUsername/Q3.java", source("Q3"));
        writeZip("messy.student.zip", messy);

        roster = List.of(new StudentDetails("CLEAN", "clean.student", "clean.student@smu.edu.sg"),
                new StudentDetails("MESSY", "messy.student", "messy.student@smu.edu.sg"));
        List<StudentSubmission> submissions = loadAll();
        var results = new AssessmentGrader(new QuestionGrader(Duration.ofSeconds(60), Duration.ofSeconds(20)))
                .grade(submissions, assessment, TesterLoader.load(testers));

        for (StudentSubmission submission : submissions) {
            List<QuestionGrade> grades = results.get(submission);
            assertEquals(4, grades.size());
            for (QuestionGrade grade : grades) {
                assertEquals(GradeStatus.COMPLETED, grade.getStatus(), grade.getMessage());
                assertEquals(1, grade.getEarnedMarks(), grade.getQuestionId());
            }
        }
    }

    //#endregion

    //#region helpers

    private Map<String, Object> answers(String prefix) {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (String file : ANSWER_FILES) {
            entries.put(prefix + file, source(className(file)));
        }
        return entries;
    }

    private static String source(String className) {
        return "// Name: " + NAME + "\n// Email ID: " + EMAIL + "\n"
                + "public class " + className + " { public static int answer() { return 1; } }\n";
    }

    private static String className(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - ".java".length());
    }

    private StudentSubmission load(String zipName, Map<String, Object> entries) throws IOException {
        writeZip(zipName, entries);
        return loadAll().getFirst();
    }

    private List<StudentSubmission> loadAll() throws IOException {
        SubmissionFileFinder finder = new SubmissionFileFinder();
        SubmissionValidator validator = new SubmissionValidator(
                List.of(new QuestionFolderRule(), new RequiredFileRule(finder), new FileNamingRule(finder), new StudentHeaderRule(finder)),
                List.of(new UnexpectedFileRule()), finder);
        List<StudentSubmission> submissions = SubmissionPreparer.prepare(zips, workspace, roster, assessment);
        submissions.forEach(submission -> validator.validate(submission, assessment));
        return submissions;
    }

    private void writeZip(String zipName, Map<String, Object> entries) throws IOException {
        try (OutputStream file = Files.newOutputStream(zips.resolve(zipName));
                ZipOutputStream zip = new ZipOutputStream(file)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() instanceof byte[] bytes) {
                    zip.write(bytes);
                } else if (entry.getValue() instanceof String text) {
                    zip.write(text.getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
    }

    //rewrites both copies of the name so the entry is latin-1 in a zip still flagged as utf-8
    private static void replaceUtf8NameWithLatin1(byte[] zip) {
        for (int i = 0; i + 1 < zip.length; i++) {
            if ((zip[i] & 0xFF) == 0xC3 && (zip[i + 1] & 0xFF) == 0xA9) {
                zip[i] = (byte) 0xE9;
                zip[i + 1] = (byte) '_';
            }
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static List<String> fileNames(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private static List<SubmissionIssue> errors(StudentSubmission submission) {
        return submission.getSubmissionIssues().stream().filter(issue -> issue.getSeverity() == IssueSeverity.ERROR).toList();
    }

    //the agreed output > <username>/Q1/Q1a.java with exact spelling & nothing stopping grading
    private void assertWorkingStructure(StudentSubmission submission) {
        assertEquals(List.of(), errors(submission));
        Path root = submission.getRootDirectory();
        assertEquals(USERNAME, root.getFileName().toString());
        try {
            for (String file : ANSWER_FILES) {
                Path answer = root.resolve(file);
                assertTrue(fileNames(root).contains(answer.getParent().getFileName().toString()), "folder spelling of " + file);
                assertTrue(fileNames(answer.getParent()).contains(answer.getFileName().toString()), "file spelling of " + file);
            }
        } catch (IOException error) {
            fail(error);
        }
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            assertTrue(attempt.isValid(), attempt.getQuestionDefinition().getId());
            assertEquals(attempt.getQuestionDefinition().getRequiredFiles().size(), attempt.getSubmittedFiles().size());
        }
    }

    private static void assertHasIssue(StudentSubmission submission, String code) {
        assertTrue(submission.getSubmissionIssues().stream().anyMatch(issue -> issue.getCode().equals(code)),
                "expected [" + code + "] in " + submission.getSubmissionIssues());
    }

    private static void assertNoHeaderProblems(StudentSubmission submission) {
        List<SubmissionIssue> problems = submission.getSubmissionIssues().stream()
                .filter(issue -> issue.getCode().endsWith("student header") || issue.getCode().equals("file read error")).toList();
        assertEquals(List.of(), problems);
    }

    //every question not named must stay gradable
    private void assertBlockedQuestions(StudentSubmission submission, String... questionIds) {
        List<String> blocked = new ArrayList<>();
        boolean wholeSubmission = errors(submission).stream().anyMatch(issue -> issue.getQuestionId() == null);
        for (QuestionDefinition question : assessment.getQuestions()) {
            boolean hasError = errors(submission).stream().anyMatch(issue -> question.getId().equals(issue.getQuestionId()));
            if (wholeSubmission || hasError) {
                blocked.add(question.getId());
            }
        }
        assertEquals(List.of(questionIds), blocked);
    }

    //#endregion
}
