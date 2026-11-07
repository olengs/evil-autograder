import com.google.gson.Gson;
import java.time.Duration;
import java.util.List;
import model.GradeStatus;
import progress.GradingProgress;

final class TuiProgressReporter extends GradingProgress { //reports grading events to tui while the junit test runs in maven
    private static final Gson JSON = new Gson();

    private void emit(Object... event) {
        try {
            Object[] reported = java.util.Arrays.copyOf(event, event.length + 1);
            reported[event.length] = java.util.Map.of("mavenThreads", java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("autograder.progress.file")),
                    JSON.toJson(reported) + "\n", java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    @Override
    public synchronized void preparing(String message) {
        super.preparing(message);
        emit("preparing", message);
    }

    @Override
    public synchronized void validationStarted(String student) {
        super.validationStarted(student);
        emit("validation", student);
    }

    @Override
    public synchronized void batchStarted(int students, List<String> questions) {
        super.batchStarted(students, questions);
        emit("batch", students, questions);
    }

    @Override
    public synchronized void submissionStarted(String student) {
        super.submissionStarted(student);
        emit("submission", student);
    }

    @Override
    public synchronized void questionStarted(String question) {
        super.questionStarted(question);
        emit("question", question);
    }

    @Override
    public synchronized void compilationStarted(String question, Duration timeout) {
        super.compilationStarted(question, timeout);
        emit("compilation", question, timeout.toMillis());
    }

    @Override
    public synchronized void executionStarted(String question, Duration timeout) {
        super.executionStarted(question, timeout);
        emit("execution", question, timeout.toMillis());
    }

    @Override
    public synchronized void questionFinished(String question, GradeStatus status) {
        super.questionFinished(question, status);
        emit("questionFinished", question, status.name());
    }

    @Override
    public synchronized void submissionFinished() {
        super.submissionFinished();
        emit("submissionFinished");
    }

    @Override
    public synchronized void batchFinished() {
        super.batchFinished();
        emit("batchFinished");
    }

    @Override
    public synchronized void failed(String message) {
        super.failed(message);
        emit("failed", message);
    }
}
