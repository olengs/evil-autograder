package generation.progress;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import generation.GenerationListener;
import generation.QuestionGenerationOutcome;

/// Worker callbacks update state; rendering only receives immutable copies.
public final class GenerationProgress implements GenerationListener {
    private final long started = System.nanoTime();
    private long stopped;
    private String message = "checking generation settings", current = "";
    private List<String> questions = List.of();
    private final Map<String, QuestionGenerationOutcome> outcomes = new LinkedHashMap<>();
    private boolean finished, failed;

    @Override public synchronized void started(List<String> ids) { questions = List.copyOf(ids); }
    @Override public synchronized void stageChanged(String value) { message = value; }
    @Override public synchronized void questionStarted(String id) { current = id; }
    @Override public synchronized void questionFinished(QuestionGenerationOutcome result) { outcomes.put(result.questionId(), result); }

    public synchronized void finish(String detail, boolean failure) {
        message = detail;
        failed = failure;
        finished = true;
        stopped = System.nanoTime();
    }

    public synchronized GenerationSnapshot snapshot() {
        return new GenerationSnapshot(message, current, questions, List.copyOf(outcomes.values()),
                Duration.ofNanos((finished ? stopped : System.nanoTime()) - started), finished, failed);
    }
}
