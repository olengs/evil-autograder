package grading.progress;

import grading.GradeStatus;
import java.time.Duration;
import java.util.List;

public interface ProgressListener { //defines optional callbacks for grading stages & outcomes
    ProgressListener NONE = new ProgressListener() {};

    default void preparing(String message) {}
    default void validationStarted(String student) {}
    default void batchStarted(int students, List<String> questions) {}
    default void submissionStarted(String student) {}
    default void questionStarted(String question) {}
    default void compilationStarted(String question, Duration timeout) {}
    default void executionStarted(String question, Duration timeout) {}
    default void questionFinished(String question, GradeStatus status) {}
    default void submissionFinished() {}
    default void batchFinished() {}
    default void failed(String message) {}
}
