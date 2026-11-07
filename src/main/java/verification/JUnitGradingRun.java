package verification;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import config.Config;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import progress.GradingProgress;
import progress.JUnitSnapshot;

/** Executes the shared Jupiter suite in-process while grading events keep the TUI live. */
public final class JUnitGradingRun {
    private static final String PARALLELISM = "4";

    private JUnitGradingRun() {}

    public static TestExecutionSummary run(Config config, GradingProgress progress) throws IOException, InterruptedException {
        return run(config, progress, false);
    }

    public static TestExecutionSummary run(Config config, GradingProgress progress, boolean printResults)
            throws IOException, InterruptedException {
        String session = GradingSession.register(config, progress, printResults);
        var listener = new UiListener(progress);
        listener.publish(); //enable JUnit before grading can report batch completion
        try {
            TestExecutionSummary summary = executeChecks(session, listener);
            checkSummary(summary, listener);
            progress.junitFinished();
            return summary;
        } catch (InterruptedException failure) {
            progress.failed("grading interrupted");
            throw failure;
        } catch (IOException | RuntimeException | Error failure) {
            progress.failed(failureMessage(failure));
            throw failure;
        } finally {
            GradingSession.release(session);
            progress.finishElapsedTime();
        }
    }

    private static TestExecutionSummary executeChecks(String session, UiListener listener) {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(GradingChecks.class))
                .configurationParameter(GradingSession.SESSION_PARAMETER, session)
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", PARALLELISM)
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.max-pool-size", PARALLELISM)
                .build();
        var summary = new SummaryGeneratingListener();
        // Keep external service-loaded listeners from printing over the terminal UI.
        var config = LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false).build();
        try (var launcher = LauncherFactory.openSession(config)) {
            Thread cancellation = listener.watchCancellation(Thread.currentThread());
            try {
                launcher.getLauncher().execute(request, summary, listener);
            } finally {
                cancellation.interrupt();
            }
        }
        return summary.getSummary();
    }

    private static void checkSummary(TestExecutionSummary summary, UiListener listener) throws IOException, InterruptedException {
        // Jupiter may clear the interrupt flag while recording a setup failure.
        if (Thread.currentThread().isInterrupted()
                || summary.getFailures().stream().anyMatch(failure -> wasInterrupted(failure.getException()))) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("JUnit grading interrupted");
        }
        listener.complete(summary);
        boolean complete = summary.getTestsFoundCount() > 0
                && summary.getTestsSucceededCount() == summary.getTestsFoundCount()
                && summary.getTotalFailureCount() == 0;
        if (!complete) {
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
                String message = result.getThrowable().map(JUnitGradingRun::failureMessage).orElse(result.getStatus().name());
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
