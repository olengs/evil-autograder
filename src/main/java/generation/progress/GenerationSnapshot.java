package generation.progress;

import java.time.Duration;
import java.util.List;

import generation.QuestionGenerationOutcome;

/// Immutable generation status read by the terminal render thread.
public record GenerationSnapshot(String message, String currentQuestion, List<String> questions,
        List<QuestionGenerationOutcome> outcomes, Duration elapsed, boolean finished, boolean failed) {
    public GenerationSnapshot {
        questions = List.copyOf(questions);
        outcomes = List.copyOf(outcomes);
    }
    public double ratio() { return questions.isEmpty() ? 0 : (double) outcomes.size() / questions.size(); }
}
