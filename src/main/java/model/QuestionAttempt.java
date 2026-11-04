// Stores a question's directory, selected student files, and issues.
// Reports whether its own findings contain an error.

package model;

import java.nio.file.Path;
import java.util.List;

public class QuestionAttempt { //stores a students answer files & any problems found for one question
    private final QuestionDefinition questionDefinition;
    private final Path questionDirectory;
    private final List<Path> submittedFiles;
    private final List<SubmissionIssue> issues;

    public QuestionAttempt(QuestionDefinition questionDefinition, Path questionDirectory, List<Path> submittedFiles, List<SubmissionIssue> issues) {
        this.questionDefinition = questionDefinition; //@1ejun6 > store question definition
        this.questionDirectory = questionDirectory; //@1ejun6 > keep students question folder explicitly
        this.submittedFiles = List.copyOf(submittedFiles); //@1ejun6 > copy lists & prevent outside changes
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

    public List<SubmissionIssue> getIssues() {
        return issues;
    }

    public boolean isValid() {
        for (SubmissionIssue issue : issues) {
            if (issue.getSeverity() == IssueSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }
}
