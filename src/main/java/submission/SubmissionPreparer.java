package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import utils.FileUtils;

public class SubmissionPreparer { //extracts & normalizes every student zip > one unreadable zip becomes an issue instead of stopping the batch
    /**
     * @param submissionFolder > containing student zip files
     * @param workspaceFolder > where submissions will be extracted
     * @param students > students read from the scoresheet
     * @param assessment > folders & files each submission should contain
     * @return > one submission per zip ready for validation
     * @throws IOException > if the submission folder cannot be read
     */
    public static List<StudentSubmission> prepare(Path submissionFolder, Path workspaceFolder,
            List<StudentDetails> students, AssessmentDefinition assessment) throws IOException {
        Files.createDirectories(workspaceFolder);
        List<StudentSubmission> submissions = new ArrayList<>();
        StudentIdentifier studentIdentifier = new StudentIdentifier();

        for (Path zipFile : SubmissionLoader.findZipFiles(submissionFolder)) {
            StudentDetails student = studentIdentifier.identify(zipFile, students);
            Path extractionDirectory = Files.createTempDirectory(workspaceFolder, "submission-");
            submissions.add(prepareOne(new StudentSubmission(student, zipFile, extractionDirectory), assessment));
        }
        return submissions;
    }

    private static StudentSubmission prepareOne(StudentSubmission extracted, AssessmentDefinition assessment) {
        Path zipFile = extracted.getOriginalZip();
        try {
            ZipExtractor.extract(zipFile, extracted.getRootDirectory());
        } catch (IOException | RuntimeException error) { //filenames illegal on this os surface as runtime exceptions
            try {
                FileUtils.deleteDirectory(extracted.getRootDirectory());
            } catch (IOException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            extracted.addIssues(List.of(new SubmissionIssue("submission preparation error", IssueSeverity.ERROR,
                    error.toString(), null, zipFile, "preparation", false)));
            return extracted;
        }

        StudentSubmission normalized = SubmissionNormalizer.normalize(extracted, assessment);
        if (normalized.getStudent() == null) {
            normalized.addIssues(List.of(new SubmissionIssue("unknown student", IssueSeverity.ERROR,
                    "no match for " + zipFile.getFileName(), null, zipFile, "preparation", false)));
        }
        return normalized;
    }
}
