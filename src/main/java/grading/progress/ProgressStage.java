//lists preparation & grading & verification & final stages of a run
//provides shared stage names for progress tracking & terminal display

package grading.progress;

public enum ProgressStage {
    loading,
    validating,
    preparing,
    compiling,
    running,
    verifying,
    completed,
    failed
}
