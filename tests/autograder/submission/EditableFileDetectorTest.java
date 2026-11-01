package autograder.submission;

import autograder.app.Pair1;
import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.Student;
import autograder.model.StudentSubmission;
import autograder.utils.StudentHeaderReader;
import autograder.validation.AnswerFileLocator;
import autograder.validation.SubmissionValidator;
import autograder.validation.rules.FileNamingRule;
import autograder.validation.rules.QuestionFolderRule;
import autograder.validation.rules.RequiredFileRule;
import autograder.validation.rules.StudentHeaderRule;
import autograder.validation.rules.UnexpectedFileRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dependency-free regressions; run with test-editable-files.ps1. */
public final class EditableFileDetectorTest {
    private static final Student ALICE = new Student("Alice", "alice", "alice@school.edu");
    private static final Student BOB = new Student("Bob", "bob", "bob@school.edu");
    private final Path temp;
    private int passed;

    private EditableFileDetectorTest(Path temp) {
        this.temp = temp;
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("editable-file-detection-test-");
        try {
            EditableFileDetectorTest test = new EditableFileDetectorTest(temp);
            test.check("leading comments, long headers and BOM", test::leadingComments);
            test.check("sample union and candidate restrictions", test::sampleUnion);
            test.check("trusted template and sample union", test::templateUnion);
            test.check("immutable definitions and repeat detection", test::immutableDefinitions);
            test.check("optional companion handoff, required-only validation", test::optionalCompanion);
            test.check("missing required answer still fails", test::missingRequiredAnswer);
            test.check("batch ZIP detection precedes validation", test::batchZipWorkflow);
            test.check("template ZIP workflow leaves submitted bytes intact", test::templateZipWorkflow);
            test.check("unmatched student remains invalid without blocking matched student", test::unmatchedStudent);
            test.check("invalid trusted template fails explicitly", test::invalidTemplate);
            System.out.println("PASS: " + test.passed + " editable-file detection tests");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void leadingComments() throws Exception {
        String source = "\uFEFF/*\n" + " * introductory text\n".repeat(20)
                + " * Name: Alice\n * Email ID: alice@school.edu\n */\nclass Answer {}\n"
                + "// Name: intruder\n// Email: intruder@school.edu\n";
        Path path = write(temp.resolve("reader/Answer.java"), source);
        byte[] original = Files.readAllBytes(path);
        var header = StudentHeaderReader.read(path);
        equal(List.of("Alice"), header.names(), "reader must include a header beyond line 15");
        equal(List.of("alice@school.edu"), header.emails(), "reader must ignore code-body comments");
        truth(Arrays.equals(original, Files.readAllBytes(path)), "reader must not rewrite source bytes");
        truth(StudentHeaderReader.parse(bytes("/* Name: \n * Email: \n */\nclass X {}"))
                .hasCommentSection(), "empty template labels must count as evidence");
        truth(!StudentHeaderReader.parse(bytes("class X {}\n// Name: Alice\n// Email: alice@school.edu"))
                .hasCommentSection(), "identity comments after code must not select a file");
    }

    private void sampleUnion() throws Exception {
        StudentSubmission first = submission("union-first", ALICE);
        StudentSubmission second = submission("union-second", BOB);
        write(first.getRootDirectory().resolve("Q1/Helper.java"), "class Helper {}\n");
        write(second.getRootDirectory().resolve("Q1/Helper.java"), header(BOB) + "class Helper {}\n");
        write(second.getRootDirectory().resolve("Q1/notes.txt"), header(BOB));
        write(second.getRootDirectory().resolve("Q1/BodyOnly.java"), "class BodyOnly {}\n" + header(BOB));
        write(second.getRootDirectory().resolve("Q1/Undeclared.java"), header(BOB));
        AssessmentDefinition input = assessment(List.of("Helper.java", "notes.txt", "BodyOnly.java"));
        QuestionDefinition detected = EditableFileDetector.detect(input, List.of(first, second))
                .getQuestions().get(0);
        equal(List.of("Answer.java", "Helper.java"), detected.getEditableFiles(),
                "one sample header must select the companion; non-Java, undeclared and in-body candidates stay out");
        equal("class Helper {}\n", Files.readString(first.getRootDirectory().resolve("Q1/Helper.java")),
                "detection must not fill in a missing identity header");
    }

    private void templateUnion() throws Exception {
        AssessmentDefinition input = assessment(List.of("Helper.java", "Other.java"));
        Path templates = temp.resolve("template-union");
        write(templates.resolve("Q1/Helper.java"), "/* Name: \n * Email: \n */\nclass Helper {}\n");
        write(templates.resolve("Q1/Other.java"), "class Other {}\n");
        StudentSubmission sample = submission("template-sample", ALICE);
        write(sample.getRootDirectory().resolve("Q1/Other.java"), header(ALICE) + "class Other {}\n");
        var detected = EditableFileDetector.detect(input, templates, List.of(sample));
        equal(List.of("Answer.java", "Helper.java", "Other.java"),
                detected.getQuestions().get(0).getEditableFiles(), "template and student evidence must be unioned");
    }

    private void immutableDefinitions() throws Exception {
        AssessmentDefinition input = assessment(List.of("Helper.java"));
        StudentSubmission sample = submission("immutable", ALICE);
        write(sample.getRootDirectory().resolve("Q1/Helper.java"), header(ALICE));
        AssessmentDefinition detected = EditableFileDetector.detect(input, List.of(sample));
        equal(List.of("Answer.java"), input.getQuestions().get(0).getEditableFiles(), "input must remain unchanged");
        var question = detected.getQuestions().get(0);
        equal(question.getEditableFiles(), EditableFileDetector.detect(detected, List.of(sample))
                .getQuestions().get(0).getEditableFiles(), "repeat detection must not duplicate paths");
        equal(List.of("Answer.java", "Helper.java"), question.withEditableFiles(List.of("Helper.java"))
                .getEditableFiles(), "required files must survive an editable-list replacement");
        try {
            question.getEditableFiles().add("Injected.java");
            throw new AssertionError("editable list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected public-model contract.
        }
    }

    private void optionalCompanion() throws Exception {
        StudentSubmission submission = submission("optional", ALICE);
        write(submission.getRootDirectory().resolve("Q1/Answer.java"), header(ALICE) + "class Answer {}\n");
        write(submission.getRootDirectory().resolve("Q1/Helper.java"), "class Helper {}\n");
        var question = assessment(List.of("Helper.java")).getQuestions().get(0)
                .withEditableFiles(List.of("Helper.java"));
        validate(submission, new AssessmentDefinition("test", List.of(question)));
        QuestionAttempt attempt = submission.getQuestionAttempts().get(0);
        equal(List.of("Answer.java", "Helper.java"), fileNames(attempt), "handoff must include the companion");
        truth(attempt.isValid() && attempt.getIssues().isEmpty(), "headerless optional companion must not fail validation");
        StudentSubmission absent = submission("optional-absent", ALICE);
        write(absent.getRootDirectory().resolve("Q1/Answer.java"), header(ALICE));
        validate(absent, new AssessmentDefinition("test", List.of(question)));
        truth(absent.getQuestionAttempts().get(0).isValid(), "absent optional companion must not become required");
    }

    private void missingRequiredAnswer() throws Exception {
        StudentSubmission submission = submission("missing-required", ALICE);
        write(submission.getRootDirectory().resolve("Q1/Helper.java"), header(ALICE));
        var question = assessment(List.of("Helper.java")).getQuestions().get(0)
                .withEditableFiles(List.of("Helper.java"));
        validate(submission, new AssessmentDefinition("test", List.of(question)));
        QuestionAttempt attempt = submission.getQuestionAttempts().get(0);
        truth(!attempt.isValid(), "an optional companion cannot replace the required answer");
        truth(attempt.getIssues().stream().anyMatch(issue -> issue.getCode().equals("missing required file")),
                "missing required answer must retain its error");
        equal(List.of("Helper.java"), fileNames(attempt), "available companion must still be handed off");
    }

    private void batchZipWorkflow() throws Exception {
        Path inputs = Files.createDirectories(temp.resolve("batch-inputs"));
        String noHeader = "class Helper {}\n";
        zip(inputs.resolve("ALICE.zip"), Map.of("Q1/Answer.java", header(ALICE), "Q1/Helper.java", noHeader));
        zip(inputs.resolve("bob.zip"), Map.of("Q1/Answer.java", header(BOB), "Q1/Helper.java", header(BOB)));
        List<StudentSubmission> results = Pair1.run(inputs, temp.resolve("batch-workspace"),
                List.of(ALICE, BOB), assessment(List.of("Helper.java")));
        equal(2, results.size(), "both ZIPs must be processed");
        equal("ALICE.zip", results.get(0).getOriginalZip().getFileName().toString(),
                "uppercase ZIP must retain its original filename despite the lowercase extraction folder");
        equal(ALICE, results.get(0).getStudent(), "uppercase ZIP must still match the roster");
        for (StudentSubmission submission : results) {
            equal(List.of("Answer.java", "Helper.java"), fileNames(submission.getQuestionAttempts().get(0)),
                    "late sample evidence must apply to every student, including the earlier ZIP");
            truth(submission.getQuestionAttempts().get(0).isValid(), "optional identity omission must remain acceptable");
        }
        equal(noHeader, Files.readString(results.get(0).getRootDirectory().resolve("Q1/Helper.java")),
                "sample union must not normalize an earlier student's header");
    }

    private void templateZipWorkflow() throws Exception {
        Path inputs = Files.createDirectories(temp.resolve("template-inputs"));
        String source = "\uFEFFclass Helper {}\r\n";
        zip(inputs.resolve("alice.zip"), Map.of("Q1/Answer.java", header(ALICE), "Q1/Helper.java", source));
        Path templates = temp.resolve("batch-template");
        write(templates.resolve("Q1/Helper.java"), "// Name: \n// Email: \nclass Helper {}\n");
        List<StudentSubmission> results = Pair1.run(inputs, temp.resolve("template-workspace"),
                List.of(ALICE), assessment(List.of("Helper.java")), templates);
        equal(List.of("Answer.java", "Helper.java"), fileNames(results.get(0).getQuestionAttempts().get(0)),
                "trusted template evidence must work even when every student omits the companion header");
        equal(source, Files.readString(results.get(0).getRootDirectory().resolve("Q1/Helper.java")),
                "BOM and line endings must survive detection and validation unchanged");
    }

    private void unmatchedStudent() throws Exception {
        Path inputs = Files.createDirectories(temp.resolve("unmatched-inputs"));
        zip(inputs.resolve("alice.zip"), Map.of("Q1/Answer.java", header(ALICE),
                "Q1/Helper.java", "class Helper {}\n"));
        zip(inputs.resolve("unknown.zip"), Map.of("Q1/Answer.java", header(BOB), "Q1/Helper.java", header(BOB)));
        List<StudentSubmission> results = Pair1.run(inputs, temp.resolve("unmatched-workspace"),
                List.of(ALICE), assessment(List.of("Helper.java")));
        equal(2, results.size(), "unknown identity must not stop processing the batch");
        QuestionAttempt matched = results.get(0).getQuestionAttempts().get(0);
        StudentSubmission unknown = results.get(1);
        QuestionAttempt unmatched = unknown.getQuestionAttempts().get(0);
        truth(matched.isValid(), "matched student's attempt must remain valid");
        equal(List.of("Answer.java", "Helper.java"), fileNames(matched),
                "matched student must receive the detected companion list");
        equal(null, unknown.getStudent(), "unknown ZIP must not be assigned an arbitrary roster identity");
        truth(!unmatched.isValid() && unmatched.getAnswerFiles().isEmpty(), "unmatched student's attempt must stay blocked");
        equal(1, unknown.getSubmissionIssues().size(), "preparation issue must not be duplicated");
        var issue = unknown.getSubmissionIssues().get(0);
        equal("submission preparation error", issue.getCode(), "original preparation issue must be preserved");
        equal(IssueSeverity.ERROR, issue.getSeverity(), "unmatched identity must remain a blocking error");
        equal(List.of(issue), unmatched.getIssues(), "unmatched attempt must carry its preparation issue");
        truth(matched.getQuestionDefinition() == unmatched.getQuestionDefinition(),
                "both attempts must reference the same detected question definition");
    }

    private void invalidTemplate() throws Exception {
        Path[] invalidTemplates = {temp.resolve("missing-template"), Files.createDirectories(temp.resolve("empty-template"))};
        for (Path invalid : invalidTemplates) {
            try {
                EditableFileDetector.detect(assessment(List.of("Helper.java")), invalid, List.of());
                throw new AssertionError("missing trusted template root or declared Java file must not silently fall back");
            } catch (IOException expected) {
                // A broken trusted template is a configuration error, unlike an omitted optional student companion.
            }
        }
    }

    private static AssessmentDefinition assessment(List<String> resources) {
        return new AssessmentDefinition("test", List.of(new QuestionDefinition("Q1", "Q1", List.of("Answer.java"), resources)));
    }

    private StudentSubmission submission(String folder, Student student) throws Exception {
        Path root = Files.createDirectories(temp.resolve(folder));
        return new StudentSubmission(student, root.resolveSibling(student.getUserName() + ".zip"), root);
    }

    private static void validate(StudentSubmission submission, AssessmentDefinition assessment) {
        var locator = new AnswerFileLocator();
        new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(locator),
                new FileNamingRule(locator), new StudentHeaderRule(locator)), List.of(new UnexpectedFileRule()), locator)
                .validate(submission, assessment);
    }

    private static List<String> fileNames(QuestionAttempt attempt) {
        return attempt.getAnswerFiles().stream().map(path -> path.getFileName().toString()).toList();
    }

    private static String header(Student student) {
        return "// Name: " + student.getName() + "\n// Email: " + student.getEmail() + "\n";
    }

    private static Path write(Path file, String source) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, source);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void zip(Path path, Map<String, String> entries) throws Exception {
        try (var stream = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                stream.putNextEntry(new ZipEntry(entry.getKey()));
                stream.write(bytes(entry.getValue()));
                stream.closeEntry();
            }
        }
    }

    private void check(String name, CheckedTest test) throws Exception {
        test.run();
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + "; expected " + expected + ", actual " + actual);
        }
    }

    private static void truth(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run() throws Exception;
    }
}
