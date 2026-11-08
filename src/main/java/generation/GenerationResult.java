package generation;

import generation.QuestionGenerationOutcome.GenerationStatus;
import java.util.List;

public final class GenerationResult { //summary of one generation run > every question's outcome & counts
    private final List<QuestionGenerationOutcome> outcomes;

    public GenerationResult(List<QuestionGenerationOutcome> outcomes) {
        //copy so the summary cannot be mutated after the run > empty when null
        this.outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    }

    public List<QuestionGenerationOutcome> getOutcomes() {
        return outcomes;
    }

    //count outcomes matching one status > used for the run summary
    public long countByStatus(GenerationStatus status) {
        return outcomes.stream().filter(outcome -> outcome.getStatus() == status).count();
    }

    public long getWrittenCount() {
        return countByStatus(GenerationStatus.WRITTEN);
    }

    //true when every question produced a written tester
    public boolean isFullySuccessful() {
        return !outcomes.isEmpty() && getWrittenCount() == outcomes.size();
    }

    @Override
    public String toString() {
        return "GenerationResult{total=" + outcomes.size()
                + ", written=" + countByStatus(GenerationStatus.WRITTEN)
                + ", validationFailed=" + countByStatus(GenerationStatus.VALIDATION_FAILED)
                + ", generationFailed=" + countByStatus(GenerationStatus.GENERATION_FAILED) + '}';
    }
}
