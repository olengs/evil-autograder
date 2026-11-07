//copies current grading status & counts & timing into an immutable ui snapshot
//retains current student outcomes without retaining submissions or process output

package grading.progress;

import grading.GradeStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ProgressSnapshot(ProgressStage stage, String message, String student, String question, List<String> questions, Map<String, GradeStatus> results, int totalStudents, int processedStudents, int processedQuestions, long stageStarted, Duration timeout, long revision, boolean totalsKnown, JUnitSnapshot junit, Duration totalElapsed, Optional<Duration> estimatedTotal) {

    public ProgressSnapshot {
        questions = List.copyOf(questions);
        results = Map.copyOf(results);
    }

    public int totalQuestions() {
        return totalStudents * questions.size();
    }

    public long elapsedSeconds() {
        return Math.max(0, (System.nanoTime() - stageStarted) / 1_000_000_000L);
    }

    public boolean isFinished() {
        return stage == ProgressStage.completed || stage == ProgressStage.failed;
    }
}
