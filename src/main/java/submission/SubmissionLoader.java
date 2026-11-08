package submission;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import assessment.AssessmentDefinition;
import utils.files.FileUtils;
import utils.process.Interruptions;
import validation.SubmissionIssue;

public final class SubmissionLoader { //extracts submissions, matches students & records preparation failures
    private SubmissionLoader() {}

    public static List<StudentSubmission> loadSubmissions(Path submissionFolder, Path workspaceFolder, List<StudentDetails> roster, AssessmentDefinition assessment) throws IOException {
        Files.createDirectories(workspaceFolder);
        List<StudentSubmission> submissions = new ArrayList<>();
        StudentIdentifier studentIdentifier = new StudentIdentifier();

        for (Path zipFile : SubmissionArchives.listZipFiles(submissionFolder)) {
            Interruptions.check("submission loading interrupted");
            StudentDetails student = studentIdentifier.identify(zipFile, roster);
            Path extractionDirectory = Files.createTempDirectory(workspaceFolder, "submission-");
            StudentSubmission submission = new StudentSubmission(student, zipFile, extractionDirectory);

            try {
                ZipExtractor.extract(zipFile, extractionDirectory);
                submission = new StudentSubmission(student, zipFile, findAnswerRoot(extractionDirectory, assessment));
                if (student == null) {
                    submission.addIssues(List.of(SubmissionIssue.error("unknown student", "no match for " + zipFile.getFileName(), null)));
                }
            } catch (IOException error) {
                if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
                try {
                    FileUtils.deleteDirectory(extractionDirectory);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
                submission.addIssues(List.of(SubmissionIssue.error("submission preparation error", error.toString(), null)));
            }
            submissions.add(submission);
        }
        return submissions;
    }

    private static Path findAnswerRoot(Path extracted, AssessmentDefinition assessment) throws IOException {
        Path candidate = extracted;
        while (true) {
            for (var question : assessment.getQuestions()) {
                if (Files.isDirectory(candidate.resolve(question.getExpectedFolderName()))) return candidate;
            }

            List<Path> children;
            try (var entries = Files.list(candidate)) {
                children = entries.filter(path -> !path.getFileName().toString().startsWith(".")).toList();
            }
            
            if (children.size() != 1 || !Files.isDirectory(children.getFirst())) return candidate;
            //unwrap one outer folder at a time > never guess between several candidates
            candidate = children.getFirst();
        }
    }
}
