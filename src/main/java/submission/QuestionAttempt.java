//stores selected student files & validation findings for one question definition
//connects validation to grading & reports whether its own findings contain a blocking error

package submission;

import assessment.QuestionDefinition;
import java.nio.file.Path;
import java.util.List;
import validation.IssueSeverity;
import validation.SubmissionIssue;

public class QuestionAttempt {
    private final QuestionDefinition questionDefinition;
    private final Path questionDirectory;
    private final List<Path> submittedFiles;
    private final List<SubmissionIssue> issues;

    public QuestionAttempt(QuestionDefinition questionDefinition, Path questionDirectory, List<Path> submittedFiles, List<SubmissionIssue> issues) {
        this.questionDefinition = questionDefinition;
        this.questionDirectory = questionDirectory;
        this.submittedFiles = List.copyOf(submittedFiles);
        this.issues = List.copyOf(issues);
    }

    public QuestionDefinition getQuestionDefinition() {
        return questionDefinition;
    }

    public Path getQuestionDirectory() {
        return questionDirectory;
    }

    public List<Path> getSubmittedFiles() {
        return submittedFiles;
    }

    public boolean isValid() {
        for (SubmissionIssue issue : issues) {
            if (issue.getSeverity() == IssueSeverity.error) {
                return false;
            }
        }
        return true;
    }
}
