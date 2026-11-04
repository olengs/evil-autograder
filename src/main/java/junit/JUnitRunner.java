package junit;

import config.Config;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import model.QuestionGrade;
import model.StudentSubmission;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class JUnitRunner { //runs JUnit against existing results or a standalone grading run
    private static final ThreadLocal<GradingRun> currentRun = new ThreadLocal<>();

    record GradingRun(Config config, Map<StudentSubmission, List<QuestionGrade>> results) {}

    private JUnitRunner() {
    }

    public static void main(String[] args) {
        runTests();
    }

    // JUnit executes on this thread so each invocation can reuse its own grading results.
    public static void verify(Config config, Map<StudentSubmission, List<QuestionGrade>> results) {
        if (currentRun.get() != null) throw new IllegalStateException("JUnit verification is already running");
        currentRun.set(new GradingRun(config, results));
        try {
            runTests();
        } finally {
            currentRun.remove();
        }
    }

    static GradingRun currentRun() {
        return currentRun.get();
    }

    private static void runTests() {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(AutograderTest.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);

        var summary = listener.getSummary();
        var writer = new PrintWriter(System.out, true);
        summary.printTo(writer);
        summary.printFailuresTo(writer);

        if (summary.getTestsFoundCount() == 0 || summary.getTotalFailureCount() > 0 || summary.getTestsSucceededCount() != summary.getTestsFoundCount()
                || summary.getContainersAbortedCount() > 0) {
            throw new IllegalStateException("junit checks did not all complete successfully");
        }
    }
}
