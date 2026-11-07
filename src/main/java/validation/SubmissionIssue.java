//describes a preparation or validation finding with a code & severity & message
//identifies affected question or uses a null question id when finding applies to submission

package validation;

public class SubmissionIssue {

    private final String code;
    private final IssueSeverity severity;
    private final String message;
    private final String questionId;

    public SubmissionIssue(String code, IssueSeverity severity, String message, String questionId) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.questionId = questionId;
    }

    public static SubmissionIssue error(String code, String message, String questionId) {
        return new SubmissionIssue(code, IssueSeverity.error, message, questionId);
    }

    public static SubmissionIssue warning(String code, String message, String questionId) {
        return new SubmissionIssue(code, IssueSeverity.warning, message, questionId);
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public boolean blocksQuestion(String questionId) {
        return severity == IssueSeverity.error && (this.questionId == null || this.questionId.equals(questionId));
    }

    @Override
    public String toString() {
        return severity + " [" + code + "] " + message;
    }
}
