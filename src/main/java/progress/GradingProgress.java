package progress;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import model.GradeStatus;

//@1ejun6 > synchronized methods let worker update progress & ui read it one at a time
//@1ejun6 > so the ui cannot read a snapshot halfway through an update
public class GradingProgress implements ProgressListener { //@1ejun6 > records grading progress & provides snapshots to ui
    private final ProgressState state = new ProgressState();
    private final Set<Long> junitThreads = ConcurrentHashMap.newKeySet();

    public void junitThreadStarted(long threadId) {
        junitThreads.add(threadId);
    }

    public Set<Long> junitThreadIds() {
        return Set.copyOf(junitThreads);
    }

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
        startQuestion(question, ProgressStage.preparing, "preparing answer files", Duration.ZERO);
    }

    @Override
    public synchronized void compilationStarted(String question, Duration timeout) {
        startQuestion(question, ProgressStage.compiling, "compiling answer & tester", timeout);
    }

    @Override
    public synchronized void executionStarted(String question, Duration timeout) {
        startQuestion(question, ProgressStage.running, "running tester", timeout);
    }

    private void startQuestion(String question, ProgressStage stage, String message, Duration timeout) {
        state.question = question;
        state.setStage(stage, message, timeout);
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
        state.setStage(state.junit.enabled() ? ProgressStage.verifying : ProgressStage.completed,
                state.junit.enabled() ? "running junit checks" : "grading finished", Duration.ZERO);
    }

    public synchronized void junitUpdated(JUnitSnapshot snapshot) {
        state.junit = snapshot;
        state.revision++;
    }

    public synchronized void junitFinished() {
        state.setStage(ProgressStage.completed, "grading & JUnit checks finished", Duration.ZERO);
    }

    @Override
    public synchronized void failed(String message) {
        state.setStage(ProgressStage.failed, message, Duration.ZERO);
    }

    public synchronized ProgressSnapshot getSnapshot() {
        return state.snapshot();
    }
}
