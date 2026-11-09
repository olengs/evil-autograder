package ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import grading.GradeStatus;
import grading.progress.GradingProgress;
import grading.progress.ProgressSnapshot;

class GradingViewTest {
    @Test
    void activeBarsAndFinalSummaryRenderWithoutVerificationState() {
        var progress = new GradingProgress();
        progress.batchStarted(2, List.of("Q1", "Q2"));
        var alice = progress.forSubmission("1", "Alice");
        var bob = progress.forSubmission("2", "Bob");
        alice.submissionStarted("Alice");
        bob.submissionStarted("Bob");
        alice.questionFinished("Q1", GradeStatus.completed);
        for (Rect area : List.of(new Rect(0, 0, 90, 30), new Rect(0, 0, 50, 24))) {
            String active = render(area, progress.getSnapshot());
            assertTrue(active.contains("Alice"));
            assertTrue(active.contains("Bob"));
            assertTrue(active.contains("█"));
        }
        assertThrows(IllegalStateException.class, progress::batchFinished);
        alice.questionFinished("Q2", GradeStatus.completed);
        alice.submissionFinished();
        String active = render(new Rect(0, 0, 90, 30), progress.getSnapshot());
        assertFalse(active.contains("Alice"));
        assertTrue(active.contains("Bob"));
        bob.questionFinished("Q1", GradeStatus.completed);
        bob.questionFinished("Q2", GradeStatus.completed);
        bob.submissionFinished();
        progress.batchFinished();
        assertFalse(progress.getSnapshot().isFinished());
        progress.completed();
        for (Rect area : List.of(new Rect(0, 0, 90, 30), new Rect(0, 0, 50, 24))) {
            String summary = render(area, progress.getSnapshot());
            assertTrue(summary.contains("grading complete"));
            assertFalse(summary.contains("JUnit"));
        }
    }

    private String render(Rect area, ProgressSnapshot snapshot) {
        Buffer buffer = Buffer.empty(area);
        new GradingView(Path.of("fixture"), snapshot).render(Frame.forTesting(buffer));
        StringBuilder text = new StringBuilder();
        for (int y = 0; y < area.height(); y++) {
            for (int x = 0; x < area.width(); x++) text.append(buffer.get(x, y).symbol());
            text.append('\n');
        }
        return text.toString();
    }
}
