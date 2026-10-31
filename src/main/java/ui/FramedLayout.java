package ui;

import static ui.UiDrawing.*;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import java.nio.file.Path;
import progress.ProgressSnapshot;
import progress.ProgressStage;
import ui.panel.BatchPanel;
import ui.panel.StudentPanel;

//ai
final class FramedLayout { //draws framed layout when terminal has room for all sections
    private FramedLayout() {}

    static void draw(Frame frame, Rect area, Path project, ProgressSnapshot snapshot,
            long displayedSecond, ResourceUsage.Sample resources) {
        int width = Math.min(area.width() - 2, 96);
        boolean compact = area.height() < 32;
        int height = Math.min(area.height() - (compact ? 0 : 2), Math.max(30, Math.min(36, snapshot.questions().size() + 25)));
        Rect outer = new Rect(area.x() + (area.width() - width) / 2, area.y() + (compact ? 0 : 1), width, height);
        Rect inner = box(frame, outer, "evil autograder");
        Rect content = new Rect(inner.x() + 1, inner.y(), inner.width() - 2, inner.height());
        drawLine(frame, content, 0, "project > " + safeText(project.toString()), muted);

        int commandY = content.y() + content.height() - 3;
        boolean extraPadding = area.height() >= 34;
        int resourceHeight = compact ? 10 : extraPadding ? 12 : 11;
        int resourceY = commandY - resourceHeight - (compact ? 0 : 1);
        int bodyY = content.y() + (compact ? 1 : 2);
        int bodyHeight = resourceY - bodyY - (compact ? 0 : 1);
        Rect body = new Rect(content.x(), bodyY, content.width(), bodyHeight);
        if (snapshot.isFinished()) {
            Rect summary = box(frame, body, "summary");
            drawSummary(frame, summary, snapshot);
        } else {
            int leftWidth = (body.width() - 1) * 2 / 5;
            Rect batch = box(frame, new Rect(body.x(), body.y(), leftWidth, body.height()), "batch progress");
            Rect student = box(frame, new Rect(body.x() + leftWidth + 1, body.y(), body.width() - leftWidth - 1, body.height()), "student");
            BatchPanel.drawBatch(frame, horizontalPadding(batch), false, snapshot);
            StudentPanel.drawStudent(frame, horizontalPadding(student), snapshot, displayedSecond);
        }

        Rect resourceInner = box(frame, new Rect(content.x(), resourceY, content.width(), resourceHeight), "resource usage");
        Rect resource = new Rect(resourceInner.x() + 1, resourceInner.y(), resourceInner.width() - 2, resourceInner.height());
        String[] metrics = resources.summary().split(" \\| ", 2);
        int metricsRow = extraPadding ? 1 : 0;
        drawLine(frame, resource, metricsRow, metrics[0], muted);
        if (metrics.length > 1) drawLine(frame, resource, metricsRow + 1, metrics[1], muted);
        int threadsRow = compact ? 2 : extraPadding ? 4 : 3;
        Block heading = Block.builder().borders(Borders.TOP_ONLY).title(" threads ").borderStyle(muted).build();
        frame.renderWidget(heading, new Rect(resource.x(), resource.y() + threadsRow, resource.width(), 1));
        drawThreads(frame, new Rect(resource.x(), resource.y() + threadsRow + 1, resource.width(), 5), resources);
        Rect commands = box(frame, new Rect(content.x(), commandY, content.width(), 3), "commands");
        drawLine(frame, commands, 0, "[enter] close  [q] close", cyan);
    }

    private static Rect box(Frame frame, Rect area, String title) {
        Block block = Block.builder().borders(Borders.ALL).title(" " + title + " ").borderStyle(muted).build();
        frame.renderWidget(block, area);
        return block.inner(area);
    }

    private static Rect horizontalPadding(Rect area) {
        return area.width() > 2 ? new Rect(area.x() + 1, area.y(), area.width() - 2, area.height()) : area;
    }

    private static void drawSummary(Frame frame, Rect area, ProgressSnapshot snapshot) {
        boolean failed = snapshot.stage() == ProgressStage.failed;
        drawLine(frame, area, 0, failed ? "grading stopped" : "grading complete", failed ? red : green);
        int totalsRow = area.height() >= 5 ? 2 : 1;
        drawLine(frame, area, totalsRow, "students processed  " + (snapshot.totalsKnown()
                ? snapshot.processedStudents() + "/" + snapshot.totalStudents() : "not available"), base);
        drawLine(frame, area, totalsRow + 1, "questions processed " + (snapshot.totalsKnown()
                ? snapshot.processedQuestions() + "/" + snapshot.totalQuestions() : "not available"), base);
        if (failed && area.height() > 5) {
            frame.renderWidget(Paragraph.builder().text(safeText(snapshot.message())).style(red).build(),
                    new Rect(area.x(), area.y() + 5, area.width(), area.height() - 5));
        }
    }

    private static void drawThreads(Frame frame, Rect area, ResourceUsage.Sample resources) {
        int[] widths = {3, 18, 14, 17, 5};
        int extra = Math.max(0, area.width() - 2 - 4 - 57);
        for (int i = 0; i < extra; i++) widths[i % widths.length]++;

        Row header = Row.from(centered("ui", widths[0]), centered("prepare & validate", widths[1]),
                centered("compile & test", widths[2]), centered("results & cleanup", widths[3]), centered("other", widths[4])).bottomMargin(1);
        Row counts = Row.from(centered(resources.ui(), widths[0]), centered(resources.prepare(), widths[1]),
                centered(resources.grading(), widths[2]), centered(resources.results(), widths[3]), centered(resources.other(), widths[4]));
        Table table = Table.builder().header(header).rows(counts).columnSpacing(1)
                .widths(Constraint.length(widths[0]), Constraint.length(widths[1]), Constraint.length(widths[2]),
                        Constraint.length(widths[3]), Constraint.length(widths[4]))
                .block(Block.builder().borders(Borders.ALL).borderStyle(muted).build())
                .style(muted).build();
        frame.renderStatefulWidget(table, area, new TableState());

        StringBuilder divider = new StringBuilder("├");
        for (int i = 0; i < widths.length; i++) {
            divider.append("─".repeat(widths[i]));
            divider.append(i == widths.length - 1 ? "┤" : "┼");
        }
        frame.buffer().setString(area.x(), area.y() + 2, divider.toString(), muted);
        int x = area.x() + 1;
        for (int i = 0; i < widths.length - 1; i++) {
            x += widths[i];
            frame.buffer().setString(x, area.y(), "┬", muted);
            frame.buffer().setString(x, area.y() + 1, "│", muted);
            frame.buffer().setString(x, area.y() + 3, "│", muted);
            frame.buffer().setString(x, area.y() + 4, "┴", muted);
            x++;
        }
    }

    private static Cell centered(int value, int width) {
        return centered(Integer.toString(value), width);
    }

    private static Cell centered(String text, int width) {
        return Cell.from(" ".repeat(Math.max(0, (width - text.length()) / 2)) + text);
    }
}
