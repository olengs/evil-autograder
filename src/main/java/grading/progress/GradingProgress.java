package grading.progress;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import grading.GradeStatus;

public class GradingProgress implements ProgressListener { //synchronizes grading progress, student outcomes & run timing
    private final ProgressState state = new ProgressState();

    @Override
    public ProgressListener forSubmission(String id, String student) {
        return new ProgressListener() {
            private final Set<String> completed = new HashSet<>();

            @Override
            public void submissionStarted(String ignored) {
                synchronized (GradingProgress.this) {
                    if (state.activeStudents.containsKey(id)) {
                        throw new IllegalStateException("submission already active > " + id);
                    }
                    state.activeStudents.put(id, new StudentProgressSnapshot(
                            id, student, 0, state.questions.size(), "", ProgressStage.preparing));
                    state.setStage(ProgressStage.running, "grading submissions", Duration.ZERO);
                }
            }

            private void update(String question, ProgressStage stage) {
                synchronized (GradingProgress.this) {
                    if (!state.activeStudents.containsKey(id)) return;
                    state.activeStudents.put(id, new StudentProgressSnapshot(
                            id, student, completed.size(), state.questions.size(), question, stage));
                    state.revision++;
                }
            }

            @Override
            public void questionStarted(String question) { update(question, ProgressStage.preparing); }

            @Override
            public void compilationStarted(String question, Duration timeout) { update(question, ProgressStage.compiling); }

            @Override
            public void executionStarted(String question, Duration timeout) { update(question, ProgressStage.running); }

            @Override
            public void questionFinished(String question, GradeStatus status) {
                synchronized (GradingProgress.this) {
                    if (!state.activeStudents.containsKey(id) || !state.questions.contains(question)
                            || !completed.add(question)) return;
                    recordCompletedQuestion();
                    update(question, ProgressStage.preparing);
                }
            }

            @Override
            public void submissionFinished() {
                synchronized (GradingProgress.this) {
                    if (!state.activeStudents.containsKey(id)) return;
                    if (completed.size() != state.questions.size()) {
                        throw new IllegalStateException("submission finished before all questions were processed > " + id);
                    }
                    state.activeStudents.remove(id);
                    state.processedStudents++;
                    state.revision++;
                }
            }

            @Override
            public void submissionAborted() {
                synchronized (GradingProgress.this) {
                    state.activeStudents.remove(id);
                    state.revision++;
                }
            }
        };
    }

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
    public synchronized void batchStarted(int students, List<String> questions, int workerCount) {
        state.activeStudents.clear();
        state.results.clear();
        state.processedStudents = 0;
        state.processedQuestions = 0;
        state.submissionActive = false;
        state.totalStudents = students;
        state.questions = List.copyOf(questions);
        state.totalsKnown = true;
        state.student = "";
        // Rough fixed estimate: ten seconds per question attempt, shared across grading workers.
        state.estimatedTotal = Optional.of(Duration.ofSeconds(students * 5L * questions.size()).dividedBy(workerCount));
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
        recordCompletedQuestion();
        state.setStage(ProgressStage.preparing, "question processed", Duration.ZERO);
    }

    private void recordCompletedQuestion() {
        state.processedQuestions++;
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
        if (!state.activeStudents.isEmpty() || state.processedStudents != state.totalStudents || state.processedQuestions != state.totalStudents * state.questions.size()) {
            throw new IllegalStateException("batch finished before all submissions were processed");
        }
        state.setStage(ProgressStage.preparing, "grading finished", Duration.ZERO);
    }

    public synchronized void completed() {
        state.setStage(ProgressStage.completed, "grading finished & scoresheet saved", Duration.ZERO);
    }

    @Override
    public synchronized void failed(String message) {
        state.setStage(ProgressStage.failed, message, Duration.ZERO);
    }

    public synchronized ProgressSnapshot getSnapshot() {
        return state.snapshot();
    }
}
