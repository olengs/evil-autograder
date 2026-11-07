package ui.panel;

import static ui.UiDrawing.*;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import progress.ProgressSnapshot;
import dev.tamboui.widgets.gauge.Gauge;
import java.util.Locale;

public final class BatchPanel { //@1ejun6 > overall batch progress > progress bar & percentage & question count & student count
    private BatchPanel() {}

    public static void drawBatch(Frame frame, Rect area, boolean heading, ProgressSnapshot snapshot) {
        int row = heading ? 1 : 0;
        if (heading) drawLine(frame, area, 0, "batch progress", muted);
        if (!snapshot.totalsKnown()) {
            drawLine(frame, area, row, "preparing", cyan);
            drawLine(frame, area, row + 1, "totals not available yet", muted);
        } else {
            double ratio = snapshot.totalQuestions() == 0 ? 0 : (double) snapshot.processedQuestions() / snapshot.totalQuestions();
            //shows completed questions as a share of the whole batch
            frame.renderWidget(Gauge.builder().ratio(ratio).label("").style(base).gaugeStyle(cyan).build(), new Rect(area.x(), area.y() + row, area.width() - 5, 1));
            drawLine(frame, new Rect(area.x() + area.width() - 4, area.y() + row, 4, 1), 0, (int) (ratio * 100) + "%", cyan);
            drawLine(frame, area, row + 1, snapshot.processedQuestions() + "/" + snapshot.totalQuestions() + " questions", base);
            drawLine(frame, area, row + 2, snapshot.processedStudents() + "/" + snapshot.totalStudents() + " students processed", base);
        }
        String total = snapshot.estimatedTotal().map(duration -> String.format(Locale.ROOT, "~%.1fs", duration.toNanos() / 1_000_000_000.0)).orElse("estimating...");
        if (snapshot.totalsKnown() && snapshot.processedQuestions() == snapshot.totalQuestions()) {
            total = "finalizing...";
        }
        drawLine(frame, area, row + 4, (area.width() >= 31 ? "estimated total > " : "estimate > ") + total, muted);
    }

}
