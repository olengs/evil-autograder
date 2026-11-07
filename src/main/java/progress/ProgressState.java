package progress;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    Duration timeout = Duration.ZERO;
    long revision;
    ProgressSnapshot cachedSnapshot; //@1ejun6 > copy of progress information for ui
    boolean totalsKnown;
    boolean submissionActive;
    JUnitSnapshot junit = JUnitSnapshot.DISABLED;

    ProgressSnapshot snapshot() {
        //@1ejun6 > creates a snapshot only when there is no cached one \ its revision is older than the trackers revision
        if (cachedSnapshot == null || cachedSnapshot.revision() != revision) {
            cachedSnapshot = new ProgressSnapshot(stage, message, student, question, questions, results, totalStudents, studentNumber, processedStudents, processedQuestions, stageStarted, timeout, revision, totalsKnown, junit);
        }
        return cachedSnapshot;
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
