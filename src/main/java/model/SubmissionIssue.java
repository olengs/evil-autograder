package model;

import java.nio.file.Path;

public class SubmissionIssue { //describes one validation problem \ note

    private final String code;
    private final IssueSeverity severity;
    private final String message;
    private final String questionId;
    private final Path affectedPath;
    private final boolean resolved;
    private final String stage;

    public SubmissionIssue(String code, IssueSeverity severity, String message, String questionId, Path affectedPath) {
        this(code, severity, message, questionId, affectedPath, "validation", false);
    }

    /** Normalizers record the original anomaly and whether it was corrected. */
    public SubmissionIssue(String code, IssueSeverity severity, String message, String questionId,
            Path affectedPath, String stage, boolean resolved) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.questionId = questionId;
        this.affectedPath = affectedPath;
        this.stage = stage;
        this.resolved = resolved;
        if (resolved && severity == IssueSeverity.ERROR) {
            throw new IllegalArgumentException("Resolved issues must be warnings for compatibility with grading");
        }
    }

    public boolean isResolved() { return resolved; }
    public String getStage() { return stage; }

    /** Keep the issue metadata when normalization moves its affected file. */
    public SubmissionIssue withAffectedPath(Path path) {
        return new SubmissionIssue(code, severity, message, questionId, path, stage, resolved);
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
