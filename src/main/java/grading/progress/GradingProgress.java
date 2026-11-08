package grading.progress;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import grading.GradeStatus;

public class GradingProgress implements ProgressListener { //synchronizes grading progress, student outcomes & run timing
    private final ProgressState state = new ProgressState();

    public synchronized void pauseElapsedTime() {
        if (state.waitingForInput || state.timerFinished) return;
        state.inputStarted = System.nanoTime();
        state.waitingForInput = true;
        state.revision++;
    }

    public synchronized void resumeElapsedTime() {
        if (!state.waitingForInput || state.timerFinished) return;
        state.inputNanos += System.nanoTime() - state.inputStarted;
        state.waitingForInput = false;
        state.revision++;
    }

    public synchronized void finishElapsedTime() {
        if (state.timerFinished) return;
        state.runFinished = System.nanoTime();
        state.timerFinished = true;
        state.revision++;
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
        state.batchStartedElapsed = state.totalElapsed();
        state.estimatedTotal = Optional.empty();
        state.setStage(ProgressStage.preparing, "starting batch grading", Duration.ZERO);
    }

    @Override
    public synchronized void submissionStarted(String student) {
        state.student = student;
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
        //ignore repeated outcomes > waiting questions never advance progress bar
        if (!state.submissionActive || !state.questions.contains(question)) return;
        if (state.results.putIfAbsent(question, status) != null) return;
        state.processedQuestions++;
        int totalQuestions = state.totalStudents * state.questions.size();
        if (state.processedQuestions >= (totalQuestions < 3 ? 1 : 3)) {
            //averages completed questions from batch start & excludes setup waiting
            Duration elapsed = state.totalElapsed();
            Duration average = elapsed.minus(state.batchStartedElapsed).dividedBy(state.processedQuestions);
            Duration remaining = average.multipliedBy(totalQuestions - state.processedQuestions);
            //keeps first prediction so progress & summary compare same estimate with actual time
            if (state.estimatedTotal.isEmpty() && state.processedQuestions < totalQuestions) {
                state.estimatedTotal = Optional.of(elapsed.plus(remaining));
            }
        }
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
