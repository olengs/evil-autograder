package ui.panel;

import static ui.UiDrawing.*;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import progress.ProgressSnapshot;
import dev.tamboui.style.Overflow;
import dev.tamboui.widgets.paragraph.Paragraph;
import progress.ProgressStage;

public final class SummaryPanel {
    private SummaryPanel() {}

    public static void drawSummary(Frame frame, Rect area, boolean columns, ProgressSnapshot snapshot) { //@1ejun6 > summary panel
        boolean failed = snapshot.stage() == ProgressStage.failed;

        drawLine(frame, area, 3, failed ? "grading stopped" : "grading complete", failed ? red : green);
        int columnWidth = columns ? area.width() / 2 : area.width();

        drawTotal(frame, new Rect(area.x(), area.y() + 5, columnWidth, 2), "students", snapshot.processedStudents(), snapshot.totalStudents(), snapshot.totalsKnown());
        drawTotal(frame, new Rect(columns ? area.x() + columnWidth : area.x(), area.y() + (columns ? 5 : 8), columnWidth, 2),
                "questions", snapshot.processedQuestions(), snapshot.totalQuestions(), snapshot.totalsKnown());

        if (failed) {
            int messageRow = columns ? 8 : 11;
            frame.renderWidget(Paragraph.builder().text(safeText(snapshot.message()))
                    .style(red).overflow(Overflow.WRAP_CHARACTER).build(),
                    new Rect(area.x(), area.y() + messageRow, area.width(), area.height() - messageRow - 1));
        }
    }

    private static void drawTotal(Frame frame, Rect area, String name, int processed, int total, boolean totalsKnown) {
        drawLine(frame, area, 0, name + " processed", muted);
        drawLine(frame, area, 1, totalsKnown ? processed + "/" + total : "not available", base);
    }
}
