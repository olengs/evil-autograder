import static org.junit.jupiter.api.Assertions.*;

import config.AssessmentLoader;
import grading.GradingWorkspace;
import java.io.IOException;
import java.io.OutputStream;
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
import model.TesterDefinition;
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

class AssessmentLayoutTest {
    private static final StudentDetails STUDENT = new StudentDetails("Alice Example", "alice", "alice@example.edu");

    @TempDir Path temp;

    @Test
    void explicitSharedFolderSupportsArbitraryNamesAndNestedResources() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/src/Solution.java"), "public class Solution { }");
        write(resources.resolve("Q1a/src/Node.java"), "public class Node { }");
        write(resources.resolve("Q1a/src/Solution.class"), "compiled answer");
        write(resources.resolve("Q1a/src/Solution$Inner.class"), "compiled inner answer");
        write(resources.resolve("Q1a/src/Node.class"), "compiled other answer");
        write(resources.resolve("Q1a/src/Helper.java"), "class Helper { }");
        write(resources.resolve("Q1a/data/input.txt"), "input");
        manifest(resources, """
                {"name":"Custom exam","questions":[
                  {"id":"part-a","folder":"Q1a","requiredFiles":["src/Solution.java"]},
                  {"id":"part-b","folder":"Q1a","requiredFiles":["src/Node.java"]}
                ]}
                """);

        AssessmentDefinition assessment = AssessmentLoader.load(resources);

        assertEquals("Custom exam", assessment.getName());
        assertEquals(2, assessment.getQuestions().size());
        assertEquals(List.of("src/Solution.java"), assessment.getQuestions().getFirst().getRequiredFiles());
        for (QuestionDefinition question : assessment.getQuestions()) {
            assertEquals(List.of("data/input.txt", "src/Helper.java"), question.getAllowedResourceFiles());
            assertEquals(resources.resolve("Q1a").toAbsolutePath(), question.getResourceDirectory());
        }
    }

    @Test
    void conventionalLayoutStillWorksAndRetainsNestedTrustedResources() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1/Q1a.java"), "class Q1a { }");
        write(resources.resolve("Q1/Q1b.java"), "class Q1b { }");
        write(resources.resolve("Q1/Q1a.class"), "compiled answer");
        write(resources.resolve("Q1/api/index.html"), "documentation");
        write(resources.resolve("Q1/.ignored"), "hidden");

        AssessmentDefinition assessment = AssessmentLoader.load(resources);

        assertEquals(List.of("Q1a", "Q1b"), assessment.getQuestions().stream().map(QuestionDefinition::getId).toList());
        assessment.getQuestions().forEach(question -> assertEquals(List.of("api/index.html"), question.getAllowedResourceFiles()));
    }

    @Test
    void wrappedNestedLayoutPassesThroughNormalizationDetectionAndValidation() throws IOException {
        Path resources = resources();
        write(resources.resolve("sections/Q1a/src/Solution.java"), "public class Solution { }");
        write(resources.resolve("sections/Q1a/src/Companion.java"), "// Name:\n// Email ID:\nclass Companion { }");
        write(resources.resolve("sections/Q1a/data/input.txt"), "trusted data");
        manifest(resources, """
                {"name":"Friendly exam name","questions":[
                  {"id":"Q1a","folder":"sections/Q1a","requiredFiles":["src/Solution.java"]}
                ]}
                """);
        AssessmentDefinition assessment = AssessmentLoader.load(resources);
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("downloads/alice/sections/Q1a/src/Solution.java"), "public class Solution { int answer = 7; }");
        write(extracted.resolve("downloads/alice/sections/Q1a/src/Companion.java"), "class Companion { }");

        Path zips = Files.createDirectories(temp.resolve("zips"));
        writeZip(extracted, zips.resolve("alice.zip"));
        StudentSubmission submission = SubmissionPreparer.prepare(zips, temp.resolve("workspace"), List.of(STUDENT), assessment).getFirst();
        AssessmentDefinition detected = EditableFileDetector.detect(assessment, List.of(submission));
        validator().validate(submission, detected);

        QuestionAttempt attempt = submission.getQuestionAttempts().getFirst();
        Path folder = submission.getRootDirectory().resolve("sections/Q1a");
        assertTrue(attempt.isValid(), submission.getSubmissionIssues().toString());
        assertEquals(folder, attempt.getQuestionDirectory());
        assertEquals(List.of(folder.resolve("src/Solution.java"), folder.resolve("src/Companion.java")), attempt.getSubmittedFiles());
        assertTrue(Files.readString(folder.resolve("src/Solution.java")).contains("Name: Alice Example"));
        assertFalse(submission.getSubmissionIssues().stream().anyMatch(issue -> issue.getCode().equals("missing student header")));

        Path testerDirectory = Files.createDirectories(temp.resolve("testers"));
        write(testerDirectory.resolve("Q1aTester.java"), "class Q1aTester { }");
        try (GradingWorkspace workspace = new GradingWorkspace()) {
            workspace.prepare(detected.getQuestions().getFirst(), attempt,
                    new TesterDefinition("Q1a", testerDirectory, Path.of("Q1aTester.java"), "Q1aTester"));
            assertTrue(Files.readString(workspace.getDirectory().resolve("src/Solution.java")).contains("int answer = 7"));
            assertEquals("trusted data", Files.readString(workspace.getDirectory().resolve("data/input.txt")));
        }
    }

    @Test
    void nestedQuestionFolderAndSourceCapitalizationAreRepairedWithoutFlattening() throws IOException {
        AssessmentDefinition assessment = oneQuestion("sections/Q1a", "src/Solution.java");
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("alice/SECTIONS/q1A/SRC/solution.JAVA"), "public class Solution { }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().getFirst().isValid(), submission.getSubmissionIssues().toString());
        Path expected = submission.getRootDirectory().resolve("sections/Q1a/src/Solution.java");
        assertTrue(Files.isRegularFile(expected));
        try (var entries = Files.list(expected.getParent())) {
            assertEquals("Solution.java", entries.findFirst().orElseThrow().getFileName().toString());
        }
        assertTrue(submission.getSubmissionIssues().stream().anyMatch(issue -> issue.getCode().equals("filename normalized")));
    }

    @Test
    void looseUniqueAnswerIsRelocatedWithItsDeclaredParentDirectories() throws IOException {
        AssessmentDefinition assessment = oneQuestion("Q1a", "src/Solution.java");
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("Solution.java"), "public class Solution { }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().getFirst().isValid(), submission.getSubmissionIssues().toString());
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q1a/src/Solution.java")));
        assertFalse(Files.exists(submission.getRootDirectory().resolve("Solution.java")));
        assertTrue(submission.getSubmissionIssues().stream().anyMatch(issue -> issue.getCode().equals("answer file relocated")));
    }

    @Test
    void misnamedQuestionFolderIsRecognizedByNestedRequiredPath() throws IOException {
        AssessmentDefinition assessment = oneQuestion("Q1a", "src/Solution.java");
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("alice/My Answers/src/Solution.java"), "public class Solution { }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().getFirst().isValid(), submission.getSubmissionIssues().toString());
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q1a/src/Solution.java")));
    }

    @Test
    void identicalBasenamesInDifferentQuestionFoldersRemainWithTheirOwnQuestions() throws IOException {
        AssessmentDefinition assessment = repeatedBasenames();
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("alice/Q1a/src/Solution.java"), "class Solution { int answer = 1; }");
        write(extracted.resolve("alice/Q2a/src/Solution.java"), "class Solution { int answer = 2; }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid), submission.getSubmissionIssues().toString());
        assertTrue(Files.readString(submission.getRootDirectory().resolve("Q1a/src/Solution.java")).contains("answer = 1"));
        assertTrue(Files.readString(submission.getRootDirectory().resolve("Q2a/src/Solution.java")).contains("answer = 2"));
    }

    @Test
    void missingAnswerDoesNotStealAnotherQuestionsCorrectlyPlacedSolution() throws IOException {
        AssessmentDefinition assessment = repeatedBasenames();
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("alice/Q1a/src/Solution.java"), "class Solution { int answer = 1; }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().getFirst().isValid(), submission.getSubmissionIssues().toString());
        assertFalse(submission.getQuestionAttempts().getLast().isValid());
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q1a/src/Solution.java")));
        assertFalse(Files.exists(submission.getRootDirectory().resolve("Q2a/src/Solution.java")));
        assertTrue(submission.getSubmissionIssues().stream().filter(issue -> issue.getSeverity() == IssueSeverity.ERROR)
                .allMatch(issue -> "Q2a".equals(issue.getQuestionId())));
    }

    @Test
    void looseSharedBasenameIsReportedWithoutGuessingAQuestion() throws IOException {
        AssessmentDefinition assessment = repeatedBasenames();
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("Solution.java"), "class Solution { }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Solution.java")));
        assertFalse(Files.exists(submission.getRootDirectory().resolve("Q1a/src/Solution.java")));
        assertFalse(Files.exists(submission.getRootDirectory().resolve("Q2a/src/Solution.java")));
        assertEquals(2, submission.getSubmissionIssues().stream().filter(issue -> issue.getCode().equals("ambiguous answer file")).count());
    }

    @Test
    void twoCopiesOfNestedAnswerAreNotGuessedBetween() throws IOException {
        AssessmentDefinition assessment = oneQuestion("Q1a", "src/Solution.java");
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("old/src/Solution.java"), "class Solution { int answer = 1; }");
        write(extracted.resolve("new/src/Solution.java"), "class Solution { int answer = 2; }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertFalse(submission.getQuestionAttempts().getFirst().isValid());
        assertTrue(submission.getSubmissionIssues().stream().anyMatch(issue -> issue.getCode().equals("ambiguous answer file")
                && "Q1a".equals(issue.getQuestionId())));
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("old/src/Solution.java")));
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("new/src/Solution.java")));
    }

    @Test
    void sameFinalFolderNameDoesNotConfuseNestedQuestionOwnership() throws IOException {
        Path resources = resources();
        write(resources.resolve("partA/Q/Solution.java"), "class Solution { }");
        write(resources.resolve("partB/Q/Solution.java"), "class Solution { }");
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"partA/Q","requiredFiles":["Solution.java"]},
                  {"id":"Q2a","folder":"partB/Q","requiredFiles":["Solution.java"]}
                ]}
                """);
        AssessmentDefinition assessment = AssessmentLoader.load(resources);
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("alice/partA/Q/Solution.java"), "class Solution { int answer = 1; }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().getFirst().isValid(), submission.getSubmissionIssues().toString());
        assertFalse(submission.getQuestionAttempts().getLast().isValid());
        assertTrue(Files.readString(submission.getRootDirectory().resolve("partA/Q/Solution.java")).contains("answer = 1"));
        assertFalse(Files.exists(submission.getRootDirectory().resolve("partB/Q/Solution.java")));
        assertTrue(submission.getSubmissionIssues().stream().filter(issue -> issue.getSeverity() == IssueSeverity.ERROR)
                .allMatch(issue -> "Q2a".equals(issue.getQuestionId())));
    }

    @Test
    void misplacedAnswerDoesNotMakeItsContainingQuestionTheAssessmentRoot() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/src/Solution.java"), "class Solution { }");
        write(resources.resolve("Q2a/src/Node.java"), "class Node { }");
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"Q1a","requiredFiles":["src/Solution.java"]},
                  {"id":"Q2a","folder":"Q2a","requiredFiles":["src/Node.java"]}
                ]}
                """);
        AssessmentDefinition assessment = AssessmentLoader.load(resources);
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("alice/Q1a/src/Solution.java"), "class Solution { }");
        write(extracted.resolve("alice/Q1a/src/Node.java"), "class Node { }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid), submission.getSubmissionIssues().toString());
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q1a/src/Solution.java")));
        assertTrue(Files.isRegularFile(submission.getRootDirectory().resolve("Q2a/src/Node.java")));
        assertFalse(Files.exists(submission.getRootDirectory().resolve("Q1a/src/Node.java")));
    }

    @Test
    void configuredNameDoesNotHideUntouchedTemplateWhenChoosingStudentAnswers() throws IOException {
        AssessmentDefinition assessment = oneQuestion("Q1a", "src/Solution.java");
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        write(extracted.resolve("RenameToYourUsername/Q1a/src/Solution.java"), "class Solution { int answer = 0; }");
        write(extracted.resolve("working-copy/Q1a/src/Solution.java"), "class Solution { int answer = 7; }");

        StudentSubmission submission = normalizeAndValidate(extracted, assessment);

        assertTrue(submission.getQuestionAttempts().getFirst().isValid(), submission.getSubmissionIssues().toString());
        assertTrue(Files.readString(submission.getRootDirectory().resolve("Q1a/src/Solution.java")).contains("answer = 7"));
    }

    @Test
    void manifestRejectsMissingOrMalformedDefinitions() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/src/Solution.java"), "class Solution { }");
        for (String invalid : List.of("[]", "{", "{}", "{\"questions\":[]}",
                "{\"questions\":[{\"id\":\"Q1a\",\"folder\":\"Q1a\",\"requiredFiles\":[]}]}",
                "{\"questions\":[{\"id\":7,\"folder\":\"Q1a\",\"requiredFiles\":[\"src/Solution.java\"]}]}",
                "{\"questions\":[{\"id\":\"Q1a\",\"folder\":\"Q1a\",\"requiredFiles\":[\"missing.java\"]}]}")) {
            manifest(resources, invalid);
            assertThrows(IOException.class, () -> AssessmentLoader.load(resources), invalid);
        }
    }

    @Test
    void manifestRejectsEscapingAndAbsoluteQuestionPaths() throws IOException {
        Path resources = resources();
        for (String folder : List.of("../other", "/absolute", "C:/absolute", "Q1a/../Q2a", "Q1a//src")) {
            manifest(resources, "{\"questions\":[{\"id\":\"Q1a\",\"folder\":\"" + folder
                    + "\",\"requiredFiles\":[\"Solution.java\"]}]}");
            assertThrows(IOException.class, () -> AssessmentLoader.load(resources), folder);
        }
    }

    @Test
    void manifestRejectsEscapingAnswerPathsAndDuplicateAnswers() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/Solution.java"), "class Solution { }");
        for (String required : List.of("[\"../outside.java\"]", "[\"/outside.java\"]", "[\"C:/outside.java\"]",
                "[\"Solution.java\",\"solution.java\"]", "[null]")) {
            manifest(resources, "{\"questions\":[{\"id\":\"Q1a\",\"folder\":\"Q1a\",\"requiredFiles\":" + required + "}]}");
            assertThrows(IOException.class, () -> AssessmentLoader.load(resources), required);
        }
    }

    @Test
    void manifestRejectsDuplicateIdsAndOverlappingQuestionFolders() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/Solution.java"), "class Solution { }");
        write(resources.resolve("Q1a/nested/Node.java"), "class Node { }");
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"Q1a","requiredFiles":["Solution.java"]},
                  {"id":"q1A","folder":"Q1a","requiredFiles":["Solution.java"]}
                ]}
                """);
        assertThrows(IOException.class, () -> AssessmentLoader.load(resources));
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"Q1a","requiredFiles":["Solution.java"]},
                  {"id":"Q1b","folder":"Q1a/nested","requiredFiles":["Node.java"]}
                ]}
                """);
        assertThrows(IOException.class, () -> AssessmentLoader.load(resources));
    }

    @Test
    void manifestRejectsContradictoryCapitalizationInSharedPathPrefixes() throws IOException {
        Path resources = resources();
        write(resources.resolve("section/Q1/Solution.java"), "class Solution { }");
        write(resources.resolve("section/Q2/Node.java"), "class Node { }");
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"section/Q1","requiredFiles":["Solution.java"]},
                  {"id":"Q2a","folder":"Section/Q2","requiredFiles":["Node.java"]}
                ]}
                """);
        assertTrue(assertThrows(IOException.class, () -> AssessmentLoader.load(resources)).getMessage().contains("capitalization"));

        write(resources.resolve("Q1a/src/Solution.java"), "class Solution { }");
        write(resources.resolve("Q1a/SRC/Node.java"), "class Node { }");
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"Q1a","requiredFiles":["src/Solution.java","SRC/Node.java"]}
                ]}
                """);
        assertTrue(assertThrows(IOException.class, () -> AssessmentLoader.load(resources)).getMessage().contains("capitalization"));
    }

    @Test
    void manifestPathsMustMatchActualTemplateCapitalizationOnEveryPlatform() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/src/Solution.java"), "class Solution { }");
        for (String question : List.of(
                "{\"id\":\"Q1a\",\"folder\":\"q1a\",\"requiredFiles\":[\"src/Solution.java\"]}",
                "{\"id\":\"Q1a\",\"folder\":\"Q1a\",\"requiredFiles\":[\"SRC/Solution.java\"]}",
                "{\"id\":\"Q1a\",\"folder\":\"Q1a\",\"requiredFiles\":[\"src/solution.java\"]}")) {
            manifest(resources, "{\"questions\":[" + question + "]}");
            IOException error = assertThrows(IOException.class, () -> AssessmentLoader.load(resources), question);
            assertTrue(error.getMessage().contains("capitalization"), error.getMessage());
        }
    }

    private AssessmentDefinition oneQuestion(String folder, String answer) throws IOException {
        Path resources = resources();
        write(resources.resolve(folder).resolve(answer), "public class Solution { }");
        manifest(resources, "{\"name\":\"Custom exam\",\"questions\":[{\"id\":\"Q1a\",\"folder\":\"" + folder
                + "\",\"requiredFiles\":[\"" + answer + "\"]}]}");
        return AssessmentLoader.load(resources);
    }

    private AssessmentDefinition repeatedBasenames() throws IOException {
        Path resources = resources();
        write(resources.resolve("Q1a/src/Solution.java"), "class Solution { }");
        write(resources.resolve("Q2a/src/Solution.java"), "class Solution { }");
        manifest(resources, """
                {"questions":[
                  {"id":"Q1a","folder":"Q1a","requiredFiles":["src/Solution.java"]},
                  {"id":"Q2a","folder":"Q2a","requiredFiles":["src/Solution.java"]}
                ]}
                """);
        return AssessmentLoader.load(resources);
    }

    private Path resources() throws IOException {
        return Files.createDirectories(temp.resolve("RenameToYourUsername"));
    }

    private StudentSubmission normalize(Path extracted, AssessmentDefinition assessment) {
        return SubmissionNormalizer.normalize(new StudentSubmission(STUDENT, temp.resolve("alice.zip"), extracted), assessment);
    }

    private StudentSubmission normalizeAndValidate(Path extracted, AssessmentDefinition assessment) {
        StudentSubmission submission = normalize(extracted, assessment);
        return validator().validate(submission, assessment);
    }

    private static SubmissionValidator validator() {
        SubmissionFileFinder finder = new SubmissionFileFinder();
        return new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(finder),
                new FileNamingRule(finder), new StudentHeaderRule(finder)), List.of(new UnexpectedFileRule()), finder);
    }

    private static void manifest(Path resources, String json) throws IOException {
        write(resources.resolve(AssessmentLoader.MANIFEST_NAME), json);
    }

    private static void writeZip(Path folder, Path destination) throws IOException {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output);
                var files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                zip.putNextEntry(new ZipEntry(folder.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
