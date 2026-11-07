package grading.progress;

public enum ProgressStage { //defines preparation, grading, verification & final run stages
    loading,
    validating,
    preparing,
    compiling,
    running,
    verifying,
    completed,
    failed
}
