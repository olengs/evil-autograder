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

/** Loads student ZIP submissions into isolated workspace folders and records preparation failures. */
public class SubmissionLoader {
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
                submission = new StudentSubmission(student, zipFile, findAnswerRoot(extractionDirectory, assessment));
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
        try (Stream<Path> paths = Files.list(submissionFolder)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().toList();
        }
    }

}
