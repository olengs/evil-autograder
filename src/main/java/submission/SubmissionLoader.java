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
                Path answerRoot = findAnswerRoot(extractionDirectory, assessment);
                boolean identifiedFromFolder = false;
                if (student == null && !answerRoot.equals(extractionDirectory)) {
                    student = studentIdentifier.identifyFolder(answerRoot, roster);
                    identifiedFromFolder = student != null;
                }
                List<SubmissionIssue> headerIssues = new ArrayList<>();
                if (student == null) {
                    try {
                        student = studentIdentifier.identifyHeaders(answerRoot, roster, headerIssues);
                    } catch (IOException error) {
                        if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
                        headerIssues.add(SubmissionIssue.warning("header read error", "unable to inspect headers > " + error.getMessage(), null));
                    }
                }
                submission = new StudentSubmission(student, zipFile, answerRoot);
                submission.addIssues(headerIssues);
                if (identifiedFromFolder) {
                    submission.addIssues(List.of(SubmissionIssue.warning("archive name", "identified " + student.getUsername()
                            + " from extracted folder > archive named " + zipFile.getFileName(), null)));
                }
                if (student == null) {
                    boolean hasStudentNumber = SubmissionFileNames.username(zipFile).strip().matches("[0-9]{8}")
                            || (!answerRoot.equals(extractionDirectory) && answerRoot.getFileName().toString().strip().matches("[0-9]{8}"));
                    String message = "no match for " + zipFile.getFileName();
                    submission.addIssues(List.of(hasStudentNumber
                            ? SubmissionIssue.warning("unknown student", message + " > student number supplied > grading without username", null)
                            : SubmissionIssue.error("unknown student", message, null)));
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
