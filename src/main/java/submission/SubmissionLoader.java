package submission;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import assessment.AssessmentDefinition;
import utils.files.FileUtils;
import utils.logging.JsonLineLogger;
import utils.process.Interruptions;
import validation.SubmissionIssue;

/// extracts submissions, matches students & records preparation failures
public final class SubmissionLoader { 
    private SubmissionLoader() {}

    public static List<StudentSubmission> loadSubmissions(Path submissionFolder, Path workspaceFolder, List<StudentDetails> roster, AssessmentDefinition assessment, int headerSearchDepth) throws IOException {
        Files.createDirectories(workspaceFolder);
        List<StudentSubmission> submissions = new ArrayList<>();
        StudentIdentifier studentIdentifier = new StudentIdentifier();

        for (Path zipFile : SubmissionArchives.listZipFiles(submissionFolder)) {
            Interruptions.check("submission loading interrupted");
            StudentDetails student = studentIdentifier.identify(zipFile, roster);
            List<String> identifiers = new ArrayList<>();
            identifiers.add(zipFile.getFileName().toString());
            Path extractionDirectory = Files.createTempDirectory(workspaceFolder, "submission-");
            StudentSubmission submission = new StudentSubmission(student, zipFile, extractionDirectory);

            try {
                ZipExtractor.extract(zipFile, extractionDirectory);
                Path answerRoot = findAnswerRoot(extractionDirectory, assessment, identifiers);
                boolean identifiedFromFolder = false;
                if (student == null && !answerRoot.equals(extractionDirectory)) {
                    student = studentIdentifier.identifyFolder(answerRoot, roster);
                    identifiedFromFolder = student != null;
                }
                List<SubmissionIssue> headerIssues = new ArrayList<>();
                boolean conflictingIdentifiers = false;
                if (student == null) {
                    try {
                        var headerIdentification = studentIdentifier.inspectHeaders(answerRoot, roster, headerIssues, identifiers, headerSearchDepth);
                        student = headerIdentification.student();
                        conflictingIdentifiers = headerIdentification.conflicting();
                    } catch (IOException error) {
                        if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
                        headerIssues.add(SubmissionIssue.warning("header read error", "unable to inspect headers > " + error.getMessage(), null));
                    }
                }
                submission = new StudentSubmission(student, zipFile, answerRoot, identifiers, conflictingIdentifiers);
                submission.addIssues(headerIssues);
                if (identifiedFromFolder) {
                    submission.addIssues(List.of(SubmissionIssue.warning("archive name", "identified " + student.getUsername()
                            + " from extracted folder > archive named " + zipFile.getFileName(), null)));
                }
                if (student == null) {
                    submission.addIssues(List.of(SubmissionIssue.warning("unknown student",
                            submission.getIdentificationMessage() + " > grading without username", null)));
                }
            } catch (IOException error) {
                if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
                submission = new StudentSubmission(student, zipFile, extractionDirectory, identifiers, false);
                try {
                    FileUtils.deleteDirectory(extractionDirectory);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
                JsonLineLogger.getInstance().logException(JsonLineLogger.Status.ERROR,
                        "SubmissionLoader", "submission preparation failed > " + zipFile, error);
                submission.addIssues(List.of(SubmissionIssue.error("submission preparation error", error.toString(), null)));
            }
            submissions.add(submission);
        }
        return submissions;
    }

    private static Path findAnswerRoot(Path extracted, AssessmentDefinition assessment, List<String> identifiers) throws IOException {
        Path candidate = extracted;
        while (true) {
            for (var question : assessment.getQuestions()) {
                if (Files.isDirectory(candidate.resolve(question.getExpectedFolderName()))) return candidate;
            }

            List<Path> children;
            try (var entries = FileUtils.streamPaths(candidate, 1)) {
                children = entries.filter(path -> !path.getFileName().toString().startsWith(".")).toList();
            }
            
            if (children.size() != 1 || !Files.isDirectory(children.getFirst())) return candidate;
            //unwrap one outer folder at a time > never guess between several candidates
            candidate = children.getFirst();
            identifiers.add(candidate.getFileName().toString());
        }
    }
}
