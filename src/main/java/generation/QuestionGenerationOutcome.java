package generation;

import java.nio.file.Path;
import java.util.Optional;

public final class QuestionGenerationOutcome { //status, saved file & message for one question
    public enum GenerationStatus { written, validationFailed, generationFailed }

    private final String questionId;
    private final GenerationStatus status;
    private final Optional<Path> writtenFile;
    private final String message;

    public QuestionGenerationOutcome(String questionId, GenerationStatus status, Path writtenFile, String message) {
        if (status == null) throw new IllegalArgumentException("status is required");
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
