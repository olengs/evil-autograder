package ui;

import static ui.UiDrawing.*;
import config.SetupConfig;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.paragraph.Paragraph;

//shows the failed setup item & accepts a replacement path or empty input to stop
public final class SetupScreen {
    private SetupScreen() {}

    public static void draw(Frame frame, SetupConfig.Issue issue, String input) {
        frame.buffer().fill(frame.area(), new Cell(" ", base));
        if (frame.width() < 32 || frame.height() < 16) {
            frame.renderWidget(Paragraph.builder().text("terminal too small\nresize to at least 32 x 16").style(base).build(), frame.area());
            return;
        }
        int width = Math.min(frame.width() - 2, 96);
        int margin = frame.height() >= 20 ? 1 : 0;
        int height = Math.min(frame.height() - margin * 2, 18);
        Rect inner = box(frame, new Rect((frame.width() - width) / 2, margin, width, height), "evil autograder");
        Rect area = new Rect(inner.x() + 1, inner.y(), inner.width() - 2, inner.height());
        int header = height >= 18 ? 2 : 0;
        if (header > 0) drawLine(frame, area, 0, "setup > replacements apply to this run only", muted);

        int problemHeight = area.height() - header - 8;
        Rect problem = box(frame, new Rect(area.x(), area.y() + header, area.width(), problemHeight), "setup error");
        drawLine(frame, problem, 0, "setting > " + issue.setting(), muted);
        int reasonHeight = Math.max(1, problem.height() - 3);
        frame.renderWidget(Paragraph.builder().text("problem > " + safeText(issue.reason())).style(red).overflow(Overflow.WRAP_WORD).build(), new Rect(problem.x(), problem.y() + 1, problem.width(), reasonHeight));
        drawLine(frame, problem, problem.height() - 1, "expected > " + CharWidth.truncateWithEllipsis(safeText(issue.path().toString()), problem.width() - 11, CharWidth.TruncatePosition.START), base);

        Rect replacement = box(frame, new Rect(area.x(), area.y() + header + problemHeight + 1, area.width(), 3), "replacement path");
        drawLine(frame, replacement, 0, CharWidth.truncateWithEllipsis(safeText(input) + "_", replacement.width(), CharWidth.TruncatePosition.START), cyan);
        Rect commands = box(frame, new Rect(area.x(), area.y() + area.height() - 3, area.width(), 3), "commands");
        drawLine(frame, commands, 0, commands.width() >= 28 ? "[enter] submit  [ctrl+c] stop" : "[enter]  [ctrl+c] stop", cyan);
    }
}
