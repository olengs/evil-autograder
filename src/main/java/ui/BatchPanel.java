package ui;

import static ui.UiDrawing.*;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import progress.ProgressSnapshot;
import dev.tamboui.widgets.gauge.Gauge;

final class BatchPanel { //@1ejun6 > overall batch progress > progress bar & percentage & question count & student count
    private BatchPanel() {}

    static void drawBatch(Frame frame, Rect area, boolean heading, ProgressSnapshot snapshot) {
        if (!snapshot.totalsKnown()) { //@1ejun6 > if program doesnt know totals yet
            drawLine(frame, area, 0, "preparing", cyan);
            drawLine(frame, area, 1, "totals not available yet", muted);
            return;
        }

        int row = heading ? 1 : 0;
        if (heading) drawLine(frame, area, 0, "batch progress", muted);

        double ratio = snapshot.totalQuestions() == 0 ? 0 : (double) snapshot.processedQuestions() / snapshot.totalQuestions();
        //@1ejun6 > how much grading is complete

        frame.renderWidget(Gauge.builder().ratio(ratio).label("").style(base).gaugeStyle(cyan).build(), new Rect(area.x(), area.y() + row, area.width() - 5, 1));
        drawLine(frame, new Rect(area.x() + area.width() - 4, area.y() + row, 4, 1), 0, (int) (ratio * 100) + "%", cyan);
        //@1ejun6 > progress bar using ratio

        drawLine(frame, area, row + 1, snapshot.processedQuestions() + "/" + snapshot.totalQuestions() + " questions", base);
        drawLine(frame, area, row + 2, snapshot.processedStudents() + "/" + snapshot.totalStudents() + " students processed", base);
    }

}
