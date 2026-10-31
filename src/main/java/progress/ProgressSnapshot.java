package progress;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import model.GradeStatus;

//@1ejun6 > copy only current students small status list > never retain submissions \ process output
public record ProgressSnapshot(ProgressStage stage, String message, String student, String question,
        List<String> questions, Map<String, GradeStatus> results, int totalStudents,
        int studentNumber, int processedStudents, int processedQuestions,
        long stageStarted, Duration timeout, long revision, boolean totalsKnown) {

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
