package grading.progress;

public enum ProgressStage { //defines preparation, grading & final run stages
    loading,
    validating,
    preparing,
    compiling,
    running,
    completed,
    failed
}
