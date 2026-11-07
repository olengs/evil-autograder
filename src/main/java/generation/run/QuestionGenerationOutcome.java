package generation.run;

import java.nio.file.Path;
import java.util.Optional;

public final class QuestionGenerationOutcome { //result of generating one tester > status & where it went & why it failed
    public enum GenerationStatus { WRITTEN, VALIDATION_FAILED, GENERATION_FAILED }

    private final String questionId; //question this outcome belongs to > e.g. Q1a
    private final GenerationStatus status;
    private final Optional<Path> writtenFile; //present whenever a tester file was written > also for a flagged VALIDATION_FAILED one
    private final String message; //human-readable detail > failure reason or completion note

    public QuestionGenerationOutcome(String questionId, GenerationStatus status, Path writtenFile, String message) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        this.questionId = questionId == null ? "" : questionId;
        this.status = status;
        this.writtenFile = Optional.ofNullable(writtenFile);
        this.message = message == null ? "" : message;
    }

    public String getQuestionId() {
        return questionId;
    }

    public GenerationStatus getStatus() {
        return status;
    }

    public Optional<Path> getWrittenFile() {
        return writtenFile;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return questionId + " [" + status + "]"
                + (writtenFile.isPresent() ? " -> " + writtenFile.get() : "")
                + (message.isBlank() ? "" : " : " + message);
    }
}
