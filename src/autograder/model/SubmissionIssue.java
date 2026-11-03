// Represents one submission or validation finding.
// Stores its code, severity, message, question, and affected path.

package autograder.model;

import java.nio.file.Path;

public class SubmissionIssue {

    private final String code;
    private final IssueSeverity severity;
    private final String message;
    private final String questionId;
    private final Path affectedPath;

    public SubmissionIssue(String code, IssueSeverity severity, String message, String questionId, Path affectedPath) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.questionId = questionId;
        this.affectedPath = affectedPath;
    }

    public String getCode() {
        return code;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getQuestionId() {
        return questionId;
    }

    public Path getAffectedPath() {
        return affectedPath;
    }

    @Override
    public String toString() {
        return severity + " ["+ code+ "] "+ message;
    }
}
