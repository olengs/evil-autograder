// Tests ZIP extraction, roster matching, and validation together.
// Testing only; not required by the application.

package autograder.validation;

import autograder.app.Pair1;
import autograder.model.QuestionAttempt;
import autograder.model.StudentSubmission;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static autograder.validation.ValidationTestSupport.*;

public class Pair1ValidationTest {
    public static void main(String[] args) throws Exception {
        realZipIsExtractedIdentifiedValidatedAndHandedOff();
        System.out.println("Pair1ValidationTest: 1 test passed");
    }

    private static void realZipIsExtractedIdentifiedValidatedAndHandedOff() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path inbox = Files.createDirectory(fixture.root.resolve("student-submission"));
            Path workspace = fixture.root.resolve("workspace");
            Path zip = inbox.resolve("alice.zip");
            String source = HEADER + "class Answer {}\n";
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
                addEntry(output, "Q1/Answer.java", source);
                addEntry(output, "Q1/Helper.java", "class Helper {}\n");
            }
            byte[] originalZip = Files.readAllBytes(zip);
            List<StudentSubmission> results = Pair1.run(inbox, workspace, List.of(STUDENT),
                    assessment(new autograder.model.QuestionDefinition("Q1a", "Q1",
                            List.of("Answer.java"), List.of("Helper.java"))));
            check(results.size() == 1, "pipeline did not return one submission");
            StudentSubmission submission = results.get(0);
            check(submission.getStudent() == STUDENT, "roster identity was not retained");
            check(submission.getOriginalZip().equals(zip), "original ZIP path was not retained");
            check(submission.getSubmissionIssues().isEmpty(), "valid ZIP received issues");
            check(submission.getQuestionAttempts().size() == 1, "expected one question attempt");
            QuestionAttempt attempt = submission.getQuestionAttempts().get(0);
            check(attempt.isValid(), "valid ZIP failed validation");
            Path answer = workspace.resolve("alice/Q1/Answer.java");
            check(attempt.getAnswerFiles().equals(List.of(answer)), "answer handoff points at wrong extraction path");
            check(Files.readString(answer).equals(source), "extracted answer changed");
            check(java.util.Arrays.equals(originalZip, Files.readAllBytes(zip)), "original ZIP changed");
        }
    }

    private static void addEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
