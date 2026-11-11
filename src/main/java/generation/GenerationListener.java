package generation;

import java.util.List;

/// Optional callbacks for generation states
public interface GenerationListener {
    GenerationListener NONE = new GenerationListener() {};

    default void started(List<String> questionIds) {}
    default void stageChanged(String message) {}
    default void questionStarted(String questionId) {}
    default void questionFinished(QuestionGenerationOutcome outcome) {}
}
