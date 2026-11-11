package grading.progress;

/// defines preparation, grading & final run stages
public enum ProgressStage { 
    loading,
    validating,
    preparing,
    compiling,
    running,
    completed,
    failed
}
