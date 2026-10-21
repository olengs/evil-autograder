package ui.panel;

import static utils.ui.UiDrawing.*;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.Locale;
import grading.progress.ProgressSnapshot;
import grading.progress.ProgressStage;

public final class SummaryPanel { //draws final counts, verification outcomes, timing & failure details
    private SummaryPanel() {}

    public static void drawSummary(Frame frame, Rect area, ProgressSnapshot snapshot) { //shared summary for both terminal layouts
        boolean failed = snapshot.stage() == ProgressStage.failed;

        drawLine(frame, area, 0, failed ? "grading stopped" : "grading complete", failed ? red : green);
        String estimate = snapshot.estimatedTotal().map(duration -> String.format(Locale.ROOT, "~%.1fs", duration.toNanos() / 1_000_000_000.0)).orElse("unavailable");
        drawLine(frame, area, 1, (area.width() >= 29 ? "estimated total > " : "estimate > ") + estimate, muted);
        drawLine(frame, area, 2, String.format(Locale.ROOT, "actual duration > %.1fs", snapshot.totalElapsed().toNanos() / 1_000_000_000.0), muted);
        int gap = area.height() >= (snapshot.junit().enabled() ? 11 : 6) + (failed ? 1 : 0) ? 1 : 0;
        int row = 3 + gap;
        drawLine(frame, area, row++, metric("students", snapshot.totalsKnown() ? snapshot.processedStudents() + " / " + snapshot.totalStudents() : "not available"), base);
        drawLine(frame, area, row++, metric("questions", snapshot.totalsKnown() ? snapshot.processedQuestions() + " / " + snapshot.totalQuestions() : "not available"), base);
        if (snapshot.junit().enabled()) {
            row += gap;
            var junit = snapshot.junit();
            drawLine(frame, area, row++, metric("passed", junit.passed()), junit.passed() > 0 ? green : muted);
            drawLine(frame, area, row++, metric("failed", junit.failed()), junit.failed() > 0 ? red : muted);
            drawLine(frame, area, row++, metric("skipped", junit.skipped()), junit.skipped() > 0 ? amber : muted);
            drawLine(frame, area, row++, metric("aborted", junit.aborted()), junit.aborted() > 0 ? red : muted);
        }
        if (failed && area.height() > row) frame.renderWidget(Paragraph.builder().text(safeText(snapshot.message())).style(red).overflow(Overflow.WRAP_CHARACTER).build(), new Rect(area.x(), area.y() + row, area.width(), area.height() - row));
        else if (failed) drawLine(frame, area, 0, "grading stopped > " + safeText(snapshot.message()), red);
    }

    private static String metric(String label, Object value) {
        return String.format(Locale.ROOT, "%-10s%8s", label, value);
    }
}
