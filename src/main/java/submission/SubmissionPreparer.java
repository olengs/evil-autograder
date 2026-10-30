package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            List<StudentDetails> matches = studentIdentifier.findMatches(zipFile, students);
            StudentDetails student = matches.size() == 1 ? matches.getFirst() : null;
            Path extractionDirectory = Files.createTempDirectory(workspaceFolder, "submission-");
            StudentSubmission extracted = new StudentSubmission(student, zipFile, extractionDirectory);
            if (matches.size() > 1) {
                extracted.addIssues(List.of(new SubmissionIssue("ambiguous student", IssueSeverity.ERROR,
                        matches.size() + " scoresheet records match " + zipFile.getFileName()
                                + "; no student selected. Resolve duplicate scoresheet records and rerun.",
                        null, zipFile, "preparation", false)));
            }
            submissions.add(prepareOne(extracted, assessment));
        }
        markDuplicateSubmissions(submissions);
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
        if (normalized.getStudent() == null && normalized.getSubmissionIssues().stream()
                .noneMatch(issue -> issue.getCode().equals("ambiguous student"))) {
            normalized.addIssues(List.of(new SubmissionIssue("unknown student", IssueSeverity.ERROR,
                    "no match for " + zipFile.getFileName(), null, zipFile, "preparation", false)));
        }
        return normalized;
    }

    /** Never guess a winning ZIP: preserve all copies and require an explicit input selection. */
    private static void markDuplicateSubmissions(List<StudentSubmission> submissions) {
        Map<String, List<StudentSubmission>> byUsername = new LinkedHashMap<>();
        for (StudentSubmission submission : submissions) {
            if (submission.getStudent() != null) {
                String username = submission.getStudent().getUsername().strip().toLowerCase(Locale.ROOT);
                byUsername.computeIfAbsent(username, key -> new ArrayList<>()).add(submission);
            }
        }
        for (var entry : byUsername.entrySet()) {
            List<StudentSubmission> duplicates = entry.getValue();
            if (duplicates.size() < 2) continue;
            List<String> zipNames = duplicates.stream()
                    .map(submission -> submission.getOriginalZip().getFileName().toString()).toList();
            for (StudentSubmission duplicate : duplicates) {
                duplicate.addIssues(List.of(new SubmissionIssue("duplicate submission", IssueSeverity.ERROR,
                        "Multiple ZIPs for " + entry.getKey() + ": " + zipNames
                                + ". No submission selected; select one ZIP, remove the others from the input folder, and rerun.",
                        null, duplicate.getOriginalZip(), "preparation", false)));
            }
        }
    }
}
