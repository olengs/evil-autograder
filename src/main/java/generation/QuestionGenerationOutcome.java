package generation;

import java.nio.file.Path;

public record QuestionGenerationOutcome(String questionId, GenerationStatus status, Path writtenFile, String message) {
    public enum GenerationStatus { written, validationFailed, generationFailed }

    public QuestionGenerationOutcome {
        if (status == null) throw new IllegalArgumentException("status is required");
        questionId = questionId == null ? "" : questionId;
        message = message == null ? "" : message;
    }

    @Override
    public String toString() {
        return questionId + " [" + status + "]"
                + (writtenFile != null ? " -> " + writtenFile : "")
                + (message.isBlank() ? "" : " : " + message);
    }
}
