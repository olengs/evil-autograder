//describes a preparation or validation finding with a code & severity & message & affected path
//identifies affected question or uses a null question id when finding applies to submission

package validation;

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

    public static SubmissionIssue error(String code, String message, String questionId, Path affectedPath) {
        return new SubmissionIssue(code, IssueSeverity.error, message, questionId, affectedPath);
    }

    public static SubmissionIssue warning(String code, String message, String questionId, Path affectedPath) {
        return new SubmissionIssue(code, IssueSeverity.warning, message, questionId, affectedPath);
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

    public boolean blocksQuestion(String questionId) {
        return severity == IssueSeverity.error && (this.questionId == null || this.questionId.equals(questionId));
    }

    @Override
    public String toString() {
        return severity + " [" + code + "] " + message;
    }
}
