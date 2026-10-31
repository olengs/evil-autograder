package progress;

import java.time.Duration;
import java.util.List;
import model.GradeStatus;

public interface ProgressListener { //@1ejun6 > defines progress update methods graders can call > grading events reported to grading progress
    void preparing(String message);
    void validationStarted(String student);
    void batchStarted(int students, List<String> questions);
    void submissionStarted(String student);
    void questionStarted(String question);
    void compilationStarted(String question, Duration timeout);
    void executionStarted(String question, Duration timeout);
    void questionFinished(String question, GradeStatus status);
    void submissionFinished();
    void batchFinished();
    void failed(String message);
}
