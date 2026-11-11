package grading.progress;

/// stores an immutable view of student progress for UI progress bar
public record StudentProgressSnapshot(String id, String student, int completedQuestions,
        int totalQuestions, String question, ProgressStage stage) {

    public double ratio() {
        return totalQuestions == 0 ? 0 : (double) completedQuestions / totalQuestions;
    }
}
