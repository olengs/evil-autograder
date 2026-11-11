package grading.progress;

import java.time.Duration;
import java.util.List;

import grading.GradeStatus;

public interface ProgressListener { //defines optional callbacks for grading stages & outcomes
    ProgressListener NONE = new ProgressListener() {};

    default ProgressListener forSubmission(String id, String student) { return this; }

    default void preparing(String message) {}
    default void validationStarted(String student) {}
    default void setEstimatedTotal(Duration estimate) {}
    default void batchStarted(int students, List<String> questions) {}
    default void submissionStarted(String student) {}
    default void questionStarted(String question) {}
    default void compilationStarted(String question, Duration timeout) {}
    default void executionStarted(String question, Duration timeout) {}
    default void questionFinished(String question, GradeStatus status) {}
    default void submissionFinished() {}
    default void submissionAborted() {}
    default void batchFinished() {}
    default void failed(String message) {}
}
