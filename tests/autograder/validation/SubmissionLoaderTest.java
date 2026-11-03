// Tests ZIP associations, compatibility, failures, and naming.
// Testing only; not required by the application.

package autograder.validation;

import autograder.app.Pair1;
import autograder.model.StudentSubmission;
import autograder.submission.ExtractedSubmission;
import autograder.submission.SubmissionFileNames;
import autograder.submission.SubmissionLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static autograder.validation.ValidationTestSupport.*;

public class SubmissionLoaderTest {
    public static void main(String[] args) throws Exception {
        uppercaseAndAcademicPrefixKeepTheirOriginalZipAssociation();
        explicitZipListIsTheOnlyInputAndPreservesOrder();
        legacyLoaderStillReturnsExtractionDirectories();
        badZipAndExistingDestinationDoNotStopTheNextStudent();
        filenameHelpersUseLocaleIndependentFolderNames();
        System.out.println("SubmissionLoaderTest: 5 tests passed");
    }

    private static void uppercaseAndAcademicPrefixKeepTheirOriginalZipAssociation() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path inbox = Files.createDirectory(fixture.root.resolve("inbox"));
            Path uppercase = zip(inbox.resolve("ALICE.ZIP"), "Q1/Answer.java", HEADER + "class Answer {}\n");
            Path prefixed = zip(inbox.resolve("2025-2026-AliCe.zip"), "Q1/Answer.java", HEADER + "class Answer {}\n");
            byte[] uppercaseBytes = Files.readAllBytes(uppercase);
            byte[] prefixedBytes = Files.readAllBytes(prefixed);
            Path workspace = fixture.root.resolve("workspace");
            List<StudentSubmission> submissions = Pair1.run(inbox, workspace, List.of(STUDENT),
                    assessment(question("Q1a", "Answer.java")));
            check(submissions.size() == 2, "uppercase or prefixed ZIP was lost during source association");
            for (StudentSubmission submission : submissions) {
                Path zip = submission.getOriginalZip();
                String folder = zip.equals(uppercase) ? "alice" : "2025-2026-alice";
                check(zip.equals(uppercase) || zip.equals(prefixed), "incorrect source archive recorded");
                check(submission.getStudent() == STUDENT, "roster identity was not found");
                check(submission.getRootDirectory().equals(workspace.resolve(folder)), "wrong extracted root");
                check(submission.getSubmissionIssues().isEmpty(), "valid submission received issues");
                check(submission.getQuestionAttempts().get(0).getAnswerFiles()
                        .equals(List.of(workspace.resolve(folder).resolve("Q1/Answer.java"))), "wrong answer handoff");
            }
            check(Arrays.equals(uppercaseBytes, Files.readAllBytes(uppercase)), "uppercase original ZIP changed");
            check(Arrays.equals(prefixedBytes, Files.readAllBytes(prefixed)), "prefixed original ZIP changed");
        }
    }

    private static void explicitZipListIsTheOnlyInputAndPreservesOrder() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path inbox = Files.createDirectory(fixture.root.resolve("inbox"));
            Path first = zip(inbox.resolve("bob.zip"), "Q1/Answer.java", "first");
            Path second = zip(inbox.resolve("alice.zip"), "Q1/Answer.java", "second");
            zip(inbox.resolve("not-selected.zip"), "Q1/Answer.java", "must not extract");
            Path workspace = fixture.root.resolve("workspace");
            List<ExtractedSubmission> results = SubmissionLoader.loadExtractedSubmissions(
                    List.of(first, second), workspace);
            check(results.stream().map(ExtractedSubmission::originalZip).toList().equals(List.of(first, second)),
                    "loader rediscovered inputs or changed caller order");
            check(results.get(0).rootDirectory().equals(workspace.resolve("bob")), "wrong first destination");
            check(Files.readString(results.get(0).rootDirectory().resolve("Q1/Answer.java")).equals("first"),
                    "source archive was paired with another student's extracted contents");
            check(Files.readString(results.get(1).rootDirectory().resolve("Q1/Answer.java")).equals("second"),
                    "second extracted contents were lost");
            check(!Files.exists(workspace.resolve("not-selected")), "loader ignored explicit ZIP selection");
        }
    }

    private static void legacyLoaderStillReturnsExtractionDirectories() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path inbox = Files.createDirectory(fixture.root.resolve("inbox"));
            Path archive = zip(inbox.resolve("ALICE.zip"), "Q1/Answer.java", "legacy content");
            byte[] original = Files.readAllBytes(archive);
            fixture.write("inbox/readme.txt", "not a ZIP");
            Files.createDirectory(inbox.resolve("directory.zip"));
            Path workspace = fixture.root.resolve("workspace");
            List<Path> roots = SubmissionLoader.loadSubmissions(inbox, workspace);
            check(roots.equals(List.of(workspace.resolve("alice"))), "legacy return type or filtering changed");
            check(Files.readString(roots.get(0).resolve("Q1/Answer.java")).equals("legacy content"),
                    "legacy adapter did not extract the ZIP");
            check(Arrays.equals(original, Files.readAllBytes(archive)), "legacy adapter altered the ZIP");
        }
    }

    private static void badZipAndExistingDestinationDoNotStopTheNextStudent() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path inbox = Files.createDirectory(fixture.root.resolve("inbox"));
            Path invalidName = zip(inbox.resolve(".zip"), "Q1/Answer.java", "invalid name");
            Path unsafe = zip(inbox.resolve("unsafe.zip"), "../escaped.txt", "must not escape");
            Path collision = zip(inbox.resolve("collision.zip"), "Q1/Answer.java", "must not overwrite");
            Path good = zip(inbox.resolve("alice.zip"), "Q1/Answer.java", "good answer");
            Path marker = fixture.write("workspace/collision/Q1/Answer.java", "existing work");
            Path workspace = fixture.root.resolve("workspace");
            List<ExtractedSubmission> results = SubmissionLoader.loadExtractedSubmissions(
                    List.of(invalidName, unsafe, collision, good), workspace);
            check(results.size() == 1 && results.get(0).originalZip().equals(good),
                    "one ZIP failure stopped later processing or returned a failed extraction");
            check(Files.readString(marker).equals("existing work"), "destination collision overwrote contents");
            check(!Files.exists(workspace.resolve("escaped.txt")), "unsafe ZIP entry escaped its destination");
            check(Files.readString(results.get(0).rootDirectory().resolve("Q1/Answer.java")).equals("good answer"),
                    "subsequent valid ZIP was not extracted");
        }
    }

    private static void filenameHelpersUseLocaleIndependentFolderNames() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Path zip = Path.of("2025-2026-IVAN.ZIP");
            check(SubmissionFileNames.baseNameWithoutZipExtension(zip).equals("2025-2026-IVAN"), "ZIP base name changed identity spelling");
            check(SubmissionFileNames.extractionFolderName(zip).equals("2025-2026-ivan"),
                    "destination naming depends on the machine's default locale");
            check(SubmissionFileNames.hasZipExtension(zip), "uppercase ZIP extension was rejected");
            check(!SubmissionFileNames.hasZipExtension(Path.of("ivan.zip.bak")), "non-ZIP suffix was accepted");
            try {
                SubmissionFileNames.extractionFolderName(Path.of(".zip"));
                throw new AssertionError("empty extraction name must remain invalid");
            } catch (IOException expected) {
                // Invalid submission filenames remain per-ZIP checked extraction failures.
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    private static Path zip(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry(entry));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return file;
    }
}
