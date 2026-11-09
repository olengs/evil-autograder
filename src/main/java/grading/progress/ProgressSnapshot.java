package grading.progress;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import grading.GradeStatus;

public record ProgressSnapshot(ProgressStage stage, String message, String student, String question, List<String> questions, Map<String, GradeStatus> results, int totalStudents, int processedStudents, int processedQuestions, long stageStarted, Duration timeout, long revision, boolean totalsKnown, JUnitSnapshot junit, Duration totalElapsed, Optional<Duration> estimatedTotal, List<StudentProgressSnapshot> activeStudents) { //stores an immutable view of grading progress for UI

    public ProgressSnapshot {
        questions = List.copyOf(questions);
        results = Map.copyOf(results);
        activeStudents = List.copyOf(activeStudents);
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
