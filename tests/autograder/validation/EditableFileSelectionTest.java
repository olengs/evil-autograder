// Tests trusted-template file selection and answer handoff.
// Testing only; not required by the application.

package autograder.validation;

import autograder.app.Pair1;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static autograder.validation.ValidationTestSupport.*;

public class EditableFileSelectionTest {
    public static void main(String[] args) throws Exception {
        declaredMarkedJavaCompanionsJoinRequiredAnswers();
        requiredAnswersDoNotNeedTemplateHeaders();
        studentOnlyHeadersCannotPromoteCompanions();
        absentOptionalCompanionsDoNotBecomeRequired();
        legacyDefinitionsRemainRequiredOnly();
        wrongCaseCandidatesAndRequiredIssuesAreRetained();
        templateReadFailureBlocksHandoffAndReachesBothIssueLists();
        realZipHandsOffSelectedFilesWithoutChangingSources();
        System.out.println("EditableFileSelectionTest: 8 tests passed");
    }

    private static void declaredMarkedJavaCompanionsJoinRequiredAnswers() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path answer = f.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            Path helper = f.write("Q1/Helper.java", "class Helper {}\n");
            f.write("Q1/Unmarked.java", HEADER + "class Unmarked {}\n");
            f.write("Q1/data.txt", HEADER);
            f.write("trusted/Q1/Helper.java", "// Email ID:\nclass Helper {}\n");
            f.write("trusted/Q1/Unmarked.java", "class Unmarked {}\n");
            f.write("trusted/Q1/data.txt", "// Name:\n");
            StudentSubmission submission = validate(f, configured(f, "Helper.java", "Unmarked.java", "data.txt"));
            check(attempt(submission).getAnswerFiles().equals(List.of(answer, helper)),
                    "selection should retain required answers and only marked Java companions");
            check(submission.getSubmissionIssues().isEmpty(), "optional companion received identity validation");
        }
    }

    private static void requiredAnswersDoNotNeedTemplateHeaders() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path answer = f.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            f.write("trusted/Q1/Answer.java", "class Answer {}\n");
            StudentSubmission submission = validate(f, configured(f));
            check(attempt(submission).getAnswerFiles().equals(List.of(answer)), "unmarked required answer was dropped");
            check(attempt(submission).isValid(), "template headers must not become required-answer eligibility checks");
        }
    }

    private static void studentOnlyHeadersCannotPromoteCompanions() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path answer = f.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            f.write("Q1/Helper.java", HEADER + "class Helper {}\n");
            f.write("trusted/Q1/Helper.java", "class Helper {}\n// Name: Alice\n");
            StudentSubmission submission = validate(f, configured(f, "Helper.java"));
            check(attempt(submission).getAnswerFiles().equals(List.of(answer)),
                    "student-only identity or code-body template comments promoted an unmarked file");
        }
    }

    private static void absentOptionalCompanionsDoNotBecomeRequired() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path answer = f.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            f.write("trusted/Q1/Helper.java", "// Name:\nclass Helper {}\n");
            StudentSubmission submission = validate(f, configured(f, "Helper.java"));
            check(attempt(submission).getAnswerFiles().equals(List.of(answer)), "missing companion produced a path");
            check(submission.getSubmissionIssues().isEmpty() && attempt(submission).isValid(),
                    "missing optional companion must not produce missing-file or header issues");
        }
    }

    private static void legacyDefinitionsRemainRequiredOnly() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path answer = f.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            f.write("Q1/Helper.java", HEADER + "class Helper {}\n");
            QuestionDefinition three = question("Q1a", "Answer.java");
            QuestionDefinition four = new QuestionDefinition("Q1a", "Q1", List.of("Answer.java"), List.of("Helper.java"));
            check(three.getResourceDirectory() == null && four.getResourceDirectory() == null,
                    "legacy constructors unexpectedly supplied a trusted root");
            check(configured(f).getResourceDirectory().equals(f.root.resolve("trusted/Q1")),
                    "explicit resource root did not resolve the question folder");
            check(attempt(validate(f, four)).getAnswerFiles().equals(List.of(answer)),
                    "legacy definitions should not infer optional files from students");
        }
    }

    private static void wrongCaseCandidatesAndRequiredIssuesAreRetained() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path answer = f.write("Q1/answer.java", "class Answer {}\n");
            Path helper = f.write("Q1/helper.java", "class Helper {}\n");
            f.write("trusted/Q1/Helper.java", "// Name:\nclass Helper {}\n");
            StudentSubmission submission = validate(f, configured(f, "Helper.java"));
            SubmissionIssue issue = onlyIssue(submission.getSubmissionIssues(), "wrong file name");
            checkError(issue, "Q1a", answer);
            check(attempt(submission).getAnswerFiles().equals(List.of(answer, helper)), "case-fallback paths were lost");
            check(!attempt(submission).isValid(), "selected paths must not override required-file errors");
        }
    }

    private static void templateReadFailureBlocksHandoffAndReachesBothIssueLists() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            f.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            f.write("Q1/Helper.java", "class Helper {}\n");
            for (boolean directory : new boolean[] {false, true}) {
                if (directory) Files.createDirectories(f.root.resolve("trusted/Q1/Helper.java"));
                StudentSubmission submission = validate(f, configured(f, "Helper.java"));
                SubmissionIssue issue = onlyIssue(submission.getSubmissionIssues(), "file read error");
                checkError(issue, "Q1a", f.root.resolve("Q1"));
                check(attempt(submission).getIssues().equals(List.of(issue)), "selection error lost from attempt");
                check(attempt(submission).getAnswerFiles().isEmpty() && !attempt(submission).isValid(),
                        "unreadable trusted template should block the complete question handoff");
            }
        }
    }

    private static void realZipHandsOffSelectedFilesWithoutChangingSources() throws Exception {
        try (ValidationTestSupport f = new ValidationTestSupport()) {
            Path inbox = Files.createDirectory(f.root.resolve("inbox"));
            Path archive = inbox.resolve("alice.zip");
            String answer = HEADER + "class Answer {}\n";
            String helper = "class Helper {}\n";
            f.write("trusted/Q1/Helper.java", "// Name:\nclass Helper {}\n");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
                for (String[] entry : new String[][] {{"Q1/Answer.java", answer}, {"Q1/Helper.java", helper}}) {
                    out.putNextEntry(new ZipEntry(entry[0]));
                    out.write(entry[1].getBytes(StandardCharsets.UTF_8));
                    out.closeEntry();
                }
            }
            byte[] original = Files.readAllBytes(archive);
            Path workspace = f.root.resolve("workspace");
            List<StudentSubmission> results = Pair1.run(inbox, workspace, List.of(STUDENT),
                    assessment(configured(f, "Helper.java")));
            check(results.size() == 1 && results.get(0).getSubmissionIssues().isEmpty(), "ZIP integration failed");
            check(attempt(results.get(0)).getAnswerFiles().equals(List.of(workspace.resolve("alice/Q1/Answer.java"),
                    workspace.resolve("alice/Q1/Helper.java"))), "selected ZIP companion did not reach handoff");
            check(Files.readString(workspace.resolve("alice/Q1/Answer.java")).equals(answer)
                    && Files.readString(workspace.resolve("alice/Q1/Helper.java")).equals(helper), "sources changed");
            check(java.util.Arrays.equals(original, Files.readAllBytes(archive)), "original archive changed");
        }
    }

    private static QuestionDefinition configured(ValidationTestSupport f, String... companions) {
        return new QuestionDefinition("Q1a", "Q1", f.root.resolve("trusted"), List.of("Answer.java"), List.of(companions));
    }

    private static StudentSubmission validate(ValidationTestSupport f, QuestionDefinition question) {
        return validator(new AnswerFileLocator()).validate(f.submission(), assessment(question));
    }

    private static QuestionAttempt attempt(StudentSubmission submission) {
        return submission.getQuestionAttempts().get(0);
    }
}
