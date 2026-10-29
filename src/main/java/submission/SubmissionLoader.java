package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import model.StudentSubmission;
import model.StudentDetails;
import model.SubmissionIssue;
import model.IssueSeverity;
import model.AssessmentDefinition;
import utils.FileUtils;

/**
 * find all student zip submissions & extracts each one into its own folder inside workspace
 *
 * @param submissionFolder > containing student zip files
 * @param workspaceFolder > where submissions will be extracted
 * @return > list of extracted submission folder paths
 * @throws IOException > if files cannot be read \ extracted
 */

public class SubmissionLoader { //find & extracts student zip files > keeping track of any that fail
    public static List<StudentSubmission> loadSubmissions(Path submissionFolder, Path workspaceFolder, List<StudentDetails> students) throws IOException {
        return loadSubmissions(submissionFolder, workspaceFolder, students, null);
    }

    public static List<StudentSubmission> loadSubmissions(Path submissionFolder, Path workspaceFolder,
            List<StudentDetails> students, AssessmentDefinition assessment) throws IOException {
        Files.createDirectories(workspaceFolder);
        List<StudentSubmission> submissions = new ArrayList<>();
        StudentIdentifier studentIdentifier = new StudentIdentifier();

        for (Path zipFile : findZipFiles(submissionFolder)) {
            StudentDetails student = studentIdentifier.identify(zipFile, students);
            Path extractionDirectory = Files.createTempDirectory(workspaceFolder, "submission-");
            StudentSubmission submission = new StudentSubmission(student, zipFile, extractionDirectory);

            try {
                ZipExtractor.extract(zipFile, extractionDirectory);
                if (assessment != null) {
                    submission = new StudentSubmission(student, zipFile, findAnswerRoot(extractionDirectory, assessment));
                }
                if (student == null) {
                    submission.addIssues(List.of(new SubmissionIssue("unknown student", IssueSeverity.ERROR, "no match for " + zipFile.getFileName(), null, zipFile)));
                }
            } catch (IOException error) {
                try {
                    FileUtils.deleteDirectory(extractionDirectory);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
                submission.addIssues(List.of(new SubmissionIssue("submission preparation error", IssueSeverity.ERROR, error.toString(), null, zipFile)));
            }
            submissions.add(submission);
        }
        return submissions;
    }

    private static Path findAnswerRoot(Path extracted, AssessmentDefinition assessment) throws IOException {
        Path candidate = extracted;
        while (true) {
            for (var question : assessment.getQuestions()) {
                if (Files.isDirectory(candidate.resolve(question.getExpectedFolder()))) return candidate;
            }
            List<Path> children;
            try (var entries = Files.list(candidate)) {
                children = entries.filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(path -> !path.getFileName().toString().equals("__MACOSX")).toList();
            }
            //@1ejun6 > unwrap a single outer folder > never guess between several answer folders
            if (children.size() != 1 || !Files.isDirectory(children.getFirst())) return candidate;
            candidate = children.getFirst();
        }
    }

    public static List<Path> findZipFiles(Path submissionFolder) throws IOException {
        //@1ejun6 > find all zip files
        try (Stream<Path> paths = Files.list(submissionFolder)) { //@1ejun6 > lists files directly inside
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().toList();
        }
    }

}
