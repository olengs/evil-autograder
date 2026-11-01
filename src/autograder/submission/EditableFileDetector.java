package autograder.submission;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.utils.StudentHeaderReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selects declared Java answer companions using the union of leading identity headers. */
public final class EditableFileDetector {
    private EditableFileDetector() { }

    /** Uses the instructor's declared resource list as the trusted candidate list. */
    public static AssessmentDefinition detect(AssessmentDefinition assessment,
            List<StudentSubmission> submissions) throws IOException {
        return detect(assessment, null, submissions);
    }

    /**
     * Required answers are always selected. A declared Java resource is also selected
     * when its trusted template or any student's corresponding copy has an identity
     * comment section. A missing header in one student cannot remove another's evidence.
     *
     * @param templateRoot optional directory containing the trusted question folders
     * @throws IOException if a supplied template root or candidate cannot be read
     */
    public static AssessmentDefinition detect(AssessmentDefinition assessment, Path templateRoot,
            List<StudentSubmission> submissions) throws IOException {
        if (templateRoot != null && !Files.isDirectory(templateRoot)) {
            throw new IOException("template folder not found > " + templateRoot);
        }
        List<QuestionDefinition> questions = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            Set<String> editable = new LinkedHashSet<>(question.getRequiredFiles());
            for (String resource : question.getAllowedResourceFiles()) {
                if (!resource.endsWith(".java")) continue;
                boolean templateHeader = templateRoot != null && StudentHeaderReader.read(
                        templateRoot.resolve(question.getExpectedFolder()).resolve(resource)).hasCommentSection();
                if (templateHeader || anyHasCommentSection(submissions, question, resource)) {
                    editable.add(resource);
                }
            }
            questions.add(question.withEditableFiles(List.copyOf(editable)));
        }
        return new AssessmentDefinition(assessment.getName(), questions);
    }

    private static boolean anyHasCommentSection(List<StudentSubmission> submissions,
            QuestionDefinition question, String resource) {
        for (StudentSubmission submission : submissions) {
            Path file = submission.getRootDirectory().resolve(question.getExpectedFolder()).resolve(resource);
            try {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && StudentHeaderReader.read(file).hasCommentSection()) {
                    return true;
                }
            } catch (IOException error) {
                submission.addIssues(List.of(new SubmissionIssue("editable file read error", IssueSeverity.WARNING,
                        "Unable to inspect editable-file header: " + error.getMessage(), question.getId(), file)));
            }
        }
        return false;
    }
}
