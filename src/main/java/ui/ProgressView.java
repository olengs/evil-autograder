package ui;

import static ui.UiDrawing.*;
import ui.panel.BatchPanel;
import ui.panel.StudentPanel;
import ui.panel.SummaryPanel;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.nio.file.Path;
import progress.ProgressSnapshot;
import progress.ProgressStage;

public class ProgressView { //own view state & responsive layout > helpers draw its sections
    private final Path project;
    private ProgressSnapshot snapshot;
    private long displayedSecond;
    private ResourceUsage.Sample resources = new ResourceUsage.Sample("measuring", 0);

    public ProgressView(Path project, ProgressSnapshot initial) {
        this.project = project;
        this.snapshot = initial;
    }

    public boolean update(ProgressSnapshot next) { //@1ejun6 > checks whether screen needs to be redrawn
        long second = next.isFinished() ? 0 : next.elapsedSeconds();
        if (next.revision() == snapshot.revision() && second == displayedSecond && next.totalElapsed().equals(snapshot.totalElapsed())) return false;
        snapshot = next;
        displayedSecond = second;
        return true;
    }

    boolean updateResources(ResourceUsage.Sample next) {
        if (resources.equals(next)) return false;
        resources = next;
        return true;
    }

    public void render(Frame frame) { //@1ejun6 > draws screen
        Rect area = frame.area();
        frame.buffer().fill(area, new Cell(" ", base)); //@1ejun6 > gets terminal area & fills it with the base background
        if (area.width() < 32 || area.height() < 16) {
            frame.renderWidget(Paragraph.builder().text("terminal too small\nresize to at least 32 x 16")
                    .style(base).build(), area);
            return;
        }

        int framedHeight = snapshot.isFinished() ? (snapshot.stage() == ProgressStage.failed ? 23 : 22) : 20;
        if (area.width() >= 79 && area.height() >= framedHeight) {
            FramedLayout.draw(frame, area, project, snapshot, displayedSecond, resources);
            return;
        }

        int width = Math.min(area.width() - 4, 96);
        boolean columns = width >= 68;
        int wantedHeight = snapshot.isFinished() ? (snapshot.junit().enabled() ? 15 : 10) + (snapshot.stage() == ProgressStage.failed ? 3 : 0)
                : Math.max(14, Math.min(24, snapshot.questions().size() + (columns ? 9 : 13))) + (columns ? 0 : 3);
        int resourceMinimum = snapshot.isFinished() ? (snapshot.stage() == ProgressStage.failed ? 23 : 20) : (columns ? 19 : 22);
        boolean showResources = area.height() >= resourceMinimum;
        int height = Math.min(area.height() - 2, wantedHeight + (showResources ? 4 : 0));
        Rect content = new Rect(area.x() + (area.width() - width) / 2, area.y() + 1, width, height);

        drawLine(frame, content, 0, "evil autograder", muted);
        String path = CharWidth.truncateWithEllipsis(safeText(project.toString()), width, CharWidth.TruncatePosition.START);
        drawLine(frame, content, 1, path, muted);
        drawLine(frame, content, content.height() - 1, "[enter] close  [q] close", cyan);

        if (snapshot.isFinished()) {
            SummaryPanel.drawSummary(frame, new Rect(content.x(), content.y() + 3, content.width(), content.height() - (showResources ? 8 : 4)), snapshot);
            if (showResources) drawResources(frame, content);
            return;
        }

        Rect body = new Rect(content.x(), content.y() + 3, content.width(), content.height() - (showResources ? 9 : 5));
        int leftWidth = columns ? (body.width() - 4) * 2 / 5 : body.width();
        BatchPanel.drawBatch(frame, new Rect(body.x(), body.y(), leftWidth, body.height()), columns, snapshot);
        Rect student = columns ? new Rect(body.x() + leftWidth + 4, body.y(), body.width() - leftWidth - 4, body.height()) : new Rect(body.x(), body.y() + 7, body.width(), body.height() - 7);
        StudentPanel.drawStudent(frame, student, snapshot, displayedSecond);
        if (showResources) drawResources(frame, content);
    }

    private void drawResources(Frame frame, Rect content) {
        drawLine(frame, content, content.height() - 5, resources.summary(), muted);
        drawLine(frame, content, content.height() - 4, "threads > " + resources.threads(), muted);
    }
}
