package validation;

public class SubmissionIssue { //stores a validation finding & determines which questions it blocks

    private final String code;
    private final IssueSeverity severity;
    private final String message;
    private final String questionId;

    private SubmissionIssue(String code, IssueSeverity severity, String message, String questionId) {
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
