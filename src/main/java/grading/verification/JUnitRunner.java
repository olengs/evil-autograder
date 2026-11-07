//runs shared junit verification for console & terminal ui against one set of grading results
//publishes live ui checks & saves scoresheets after verification & preserves cancellation cleanup

package grading.verification;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import app.Autograder;
import config.Config;
import grading.QuestionGrade;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import grading.progress.GradingProgress;
import grading.progress.JUnitSnapshot;
import report.ConsoleReportPrinter;
import report.ScoreSheetWriter;
import submission.StudentSubmission;

public final class JUnitRunner {
    private static final String parallelism = "4";

    private JUnitRunner() {}

    public static void main(String[] args) {
        runTests(null);
    }

    public static void verify(Config config, Map<StudentSubmission, List<QuestionGrade>> results) {
        String session = GradingSession.register(config, results);
        try {
            runTests(session);
        } finally {
            GradingSession.release(session);
        }
    }

    private static void runTests(String session) {
        TestExecutionSummary summary = executeChecks(session, null);
        var writer = new PrintWriter(System.out, true);
        summary.printTo(writer);
        summary.printFailuresTo(writer);
        if (!isComplete(summary)) throw new IllegalStateException("junit checks did not all complete successfully");
    }

    public static TestExecutionSummary run(Config config, GradingProgress progress) throws IOException, InterruptedException {
        return run(config, progress, false);
    }

    public static TestExecutionSummary run(Config config, GradingProgress progress, boolean printResults)
            throws IOException, InterruptedException {
        String session = null;
        var listener = new UiListener(progress);
        listener.publish(); //enable JUnit before grading can report batch completion
        try {
            var results = Autograder.run(config, progress);
            if (printResults) ConsoleReportPrinter.print(results);
            session = GradingSession.register(config, results);
            TestExecutionSummary summary = executeChecks(session, listener);
            checkSummary(summary, listener);
            progress.preparing("saving scoresheet");
            ScoreSheetWriter.write(config.getScoresheetFile(), results,
                    config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName()));
            progress.junitFinished();
            return summary;
        } catch (InterruptedException failure) {
            progress.failed("grading interrupted");
            throw failure;
        } catch (IOException | RuntimeException | Error failure) {
            progress.failed(failureMessage(failure));
            throw failure;
        } finally {
            if (session != null) GradingSession.release(session);
            progress.finishElapsedTime();
        }
    }

    private static TestExecutionSummary executeChecks(String session, UiListener listener) {
        boolean ui = listener != null;
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(AutograderTest.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", Boolean.toString(ui));
        if (session != null) request.configurationParameter(GradingSession.sessionParameter, session);
        if (ui) {
            request.configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                    .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                    .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", parallelism)
                    .configurationParameter("junit.jupiter.execution.parallel.config.fixed.max-pool-size", parallelism);
        }
        var summary = new SummaryGeneratingListener();
        //external listeners may print console output > keep them away from terminal ui
        var config = LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(!ui).build();
        try (var launcher = LauncherFactory.openSession(config)) {
            Thread cancellation = ui ? listener.watchCancellation(Thread.currentThread()) : null;
            try {
                if (ui) launcher.getLauncher().execute(request.build(), summary, listener);
                else launcher.getLauncher().execute(request.build(), summary);
            } finally {
                if (cancellation != null) cancellation.interrupt();
            }
        }
        return summary.getSummary();
    }

    private static boolean isComplete(TestExecutionSummary summary) {
        return summary.getTestsFoundCount() > 0
                && summary.getTestsSucceededCount() == summary.getTestsFoundCount()
                && summary.getTotalFailureCount() == 0
                && summary.getContainersAbortedCount() == 0;
    }

    private static void checkSummary(TestExecutionSummary summary, UiListener listener) throws IOException, InterruptedException {
        // Jupiter may clear the interrupt flag while recording a setup failure.
        if (Thread.currentThread().isInterrupted()
                || summary.getFailures().stream().anyMatch(failure -> wasInterrupted(failure.getException()))) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("JUnit grading interrupted");
        }
        listener.complete(summary);
        if (!isComplete(summary)) {
            String detail = listener.failures.isEmpty() ? "not all JUnit checks completed" : listener.failures.getFirst();
            throw new IOException("JUnit checks failed > " + detail);
        }
    }

    private static String failureMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.toString() : failure.getMessage();
    }

    private static boolean wasInterrupted(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof InterruptedIOException) return true;
        }
        return false;
    }

    private static final class UiListener implements TestExecutionListener {
        private final GradingProgress progress;
        private final List<String> failures = new ArrayList<>();
        private final ConcurrentHashMap<Long, Thread> workers = new ConcurrentHashMap<>();
        private long passed, failed, skipped, aborted;
        private String currentTest = "starting JUnit";

        UiListener(GradingProgress progress) {
            this.progress = progress;
        }

        private Thread watchCancellation(Thread owner) {
            // Forward cancellation to Jupiter's pool, including the grading setup thread.
            return Thread.ofPlatform().daemon().name("junit-cancellation").start(() -> {
                try {
                    while (!owner.isInterrupted()) Thread.sleep(25);
                    workers.values().forEach(Thread::interrupt);
                } catch (InterruptedException finished) {
                    // The launcher completed normally.
                }
            });
        }

        @Override
        public synchronized void executionStarted(TestIdentifier test) {
            Thread thread = Thread.currentThread();
            workers.put(thread.threadId(), thread);
            progress.junitThreadStarted(thread.threadId());
            if (!test.isTest()) return;
            currentTest = test.getDisplayName();
            publish();
        }

        @Override
        public synchronized void executionSkipped(TestIdentifier test, String reason) {
            if (test.isTest()) skipped++;
            publish();
        }

        @Override
        public synchronized void executionFinished(TestIdentifier test, TestExecutionResult result) {
            if (test.isTest()) {
                switch (result.getStatus()) {
                    case SUCCESSFUL -> passed++;
                    case FAILED -> failed++;
                    case ABORTED -> aborted++;
                }
            }
            // Container failures include @BeforeAll/setup errors, even with zero executed tests.
            if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL && failures.size() < 3) {
                String message = result.getThrowable().map(JUnitRunner::failureMessage).orElse(result.getStatus().name());
                String detail = test.getDisplayName() + ": " + message;
                failures.add(detail.length() > 500 ? detail.substring(0, 500) + "…" : detail);
            }
            publish();
        }

        synchronized void publish() {
            progress.junitUpdated(new JUnitSnapshot(true, passed, failed, skipped, aborted,
                    currentTest, failures));
        }

        synchronized void complete(TestExecutionSummary summary) {
            passed = summary.getTestsSucceededCount();
            failed = summary.getTestsFailedCount();
            skipped = summary.getTestsSkippedCount();
            aborted = summary.getTestsAbortedCount();
            currentTest = "JUnit checks finished";
            publish();
        }
    }
}
