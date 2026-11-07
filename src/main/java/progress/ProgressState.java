package progress;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import model.GradeStatus;

final class ProgressState { //@1ejun6 > stores current progress values & reuses latest snapshot
    ProgressStage stage = ProgressStage.loading;
    String message = "loading configuration";
    String student = "";
    String question = "";
    List<String> questions = List.of();
    final Map<String, GradeStatus> results = new LinkedHashMap<>();
    int totalStudents;
    int studentNumber;
    int processedStudents;
    int processedQuestions;
    long stageStarted = System.nanoTime();
    final long runStarted = stageStarted;
    long inputStarted;
    long inputNanos;
    long runFinished;
    boolean waitingForInput;
    boolean timerFinished;
    Duration batchStartedElapsed = Duration.ZERO;
    Optional<Duration> estimatedRemaining = Optional.empty();
    Optional<Duration> estimatedTotal = Optional.empty();
    Duration timeout = Duration.ZERO;
    long revision;
    ProgressSnapshot cachedSnapshot; //@1ejun6 > copy of progress information for ui
    boolean totalsKnown;
    boolean submissionActive;
    JUnitSnapshot junit = JUnitSnapshot.DISABLED;

    ProgressSnapshot snapshot() {
        //refreshes live time every tick & reuses snapshots while paused or finished
        Duration elapsed = totalElapsed();
        if (cachedSnapshot == null || cachedSnapshot.revision() != revision || cachedSnapshot.totalElapsed().toMillis() / 250 != elapsed.toMillis() / 250) {
            cachedSnapshot = new ProgressSnapshot(stage, message, student, question, questions, results, totalStudents, studentNumber, processedStudents, processedQuestions, stageStarted, timeout, revision, totalsKnown, junit, elapsed, estimatedRemaining, estimatedTotal);
        }
        return cachedSnapshot;
    }

    Duration totalElapsed() {
        long end = timerFinished ? runFinished : System.nanoTime();
        long paused = inputNanos + (waitingForInput ? end - inputStarted : 0);
        return Duration.ofNanos(Math.max(0, end - runStarted - paused));
    }

    void setStage(ProgressStage stage, String message, Duration timeout) { //@1ejun6 > sets stage & message & timeout > resets timer & increments change number
        //@1ejun6 > method that tells program what step of grading is happening now
        this.stage = stage;
        this.message = message;
        this.timeout = timeout;
        stageStarted = System.nanoTime(); //@1ejun6 > monotonic stage timer > independent of system clock changes
        revision++;
    }
}
