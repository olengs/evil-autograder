package progress;

import java.time.Duration;
import java.util.List;
import model.GradeStatus;

//@1ejun6 > synchronized methods let worker update progress & ui read it one at a time
//@1ejun6 > so the ui cannot read a snapshot halfway through an update
public class GradingProgress implements ProgressListener { //@1ejun6 > records grading progress & provides snapshots to ui
    private final ProgressState state = new ProgressState();

    @Override
    public synchronized void preparing(String message) {
        state.setStage(ProgressStage.loading, message, Duration.ZERO);
    }

    @Override
    public synchronized void validationStarted(String student) {
        state.student = student;
        state.setStage(ProgressStage.validating, "checking submission", Duration.ZERO);
    }

    @Override
    public synchronized void batchStarted(int students, List<String> questions) {
        state.totalStudents = students;
        state.questions = List.copyOf(questions);
        state.totalsKnown = true;
        state.student = "";
        state.setStage(ProgressStage.preparing, "starting batch grading", Duration.ZERO);
    }

    @Override
    public synchronized void submissionStarted(String student) {
        state.student = student;
        state.studentNumber = state.processedStudents + 1;
        state.question = "";
        state.results.clear(); //only this student remains in UI state
        state.submissionActive = true;
        state.setStage(ProgressStage.preparing, "preparing submission", Duration.ZERO);
    }

    @Override
    public synchronized void questionStarted(String question) {
        state.question = question;
        state.setStage(ProgressStage.preparing, "preparing answer files", Duration.ZERO);
    }

    @Override
    public synchronized void compilationStarted(String question, Duration timeout) {
        state.question = question;
        state.setStage(ProgressStage.compiling, "compiling answer & tester", timeout);
    }

    @Override
    public synchronized void executionStarted(String question, Duration timeout) {
        state.question = question;
        state.setStage(ProgressStage.running, "running tester", timeout);
    }

    @Override
    public synchronized void questionFinished(String question, GradeStatus status) {
        //@1ejun6 > ignore repeated outcomes > waiting questions never advance progress bar
        if (!state.submissionActive || !state.questions.contains(question) || status == GradeStatus.pending) return;
        if (state.results.putIfAbsent(question, status) != null) return;
        state.processedQuestions++;
        state.setStage(ProgressStage.preparing, "question processed", Duration.ZERO);
    }

    @Override
    public synchronized void submissionFinished() {
        if (!state.submissionActive) return;
        if (state.results.size() != state.questions.size()) {
            throw new IllegalStateException("submission finished before all questions were processed");
        }
        state.submissionActive = false;
        state.processedStudents++;
        state.revision++;
    }

    @Override
    public synchronized void batchFinished() {
        if (state.processedStudents != state.totalStudents || state.processedQuestions != state.totalStudents * state.questions.size()) {
            throw new IllegalStateException("batch finished before all submissions were processed");
        }
        state.setStage(ProgressStage.completed, "grading finished", Duration.ZERO);
    }

    @Override
    public synchronized void failed(String message) {
        state.setStage(ProgressStage.failed, message, Duration.ZERO);
    }

    public synchronized ProgressSnapshot getSnapshot() {
        return state.snapshot();
    }
}
