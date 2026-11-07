package ui.panel;

import static ui.UiDrawing.*;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import progress.ProgressSnapshot;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import model.GradeStatus;

public final class StudentPanel { //student panel
    private StudentPanel() {
    }

    public static void drawStudent(Frame frame, Rect area, ProgressSnapshot snapshot, long displayedSecond) {
        if (snapshot.stage() == progress.ProgressStage.verifying) {
            drawLine(frame, area, 0, "running JUnit checks", cyan);
            drawLine(frame, area, 1, snapshot.junit().currentTest(), base);
            drawLine(frame, area, 2, snapshot.junit().summary(), muted);
            return;
        }
        //@1ejun6 > show current student and grading stage
        if (snapshot.student().isEmpty()) {
            drawLine(frame, area, 0, snapshot.message(), cyan);
            drawLine(frame, area, 1, "waiting for submissions", muted);
            return;
        }
        drawLine(frame, area, 0, snapshot.student(), base);
        if (area.height() == 2 && !snapshot.question().isEmpty()) {
            drawQuestion(frame, area, 1, snapshot.question(), snapshot);
            return;
        }
        String timing = snapshot.stage().name() + " · " + displayedSecond + "s";
        if (!snapshot.timeout().isZero()) timing += " / " + snapshot.timeout().toSeconds() + "s limit";
        drawLine(frame, area, 1, timing, muted);

        int questionRow = area.height() < snapshot.questions().size() + 3 && !snapshot.question().isEmpty() ? 2 : 3;
        if (snapshot.question().isEmpty()) drawLine(frame, area, 2, snapshot.message(), cyan);
        int visibleRows = Math.max(0, area.height() - questionRow);
        //@1ejun6 > follow active question automatically when whole list does not fit

        int start = Math.max(0, Math.min(snapshot.questions().indexOf(snapshot.question()), snapshot.questions().size() - visibleRows));
        int end = Math.min(snapshot.questions().size(), start + visibleRows);
        for (int index = start; index < end; index++) {
            drawQuestion(frame, area, index - start + questionRow, snapshot.questions().get(index), snapshot);
        }
    }

    private static void drawQuestion(Frame frame, Rect area, int row, String question, ProgressSnapshot snapshot) {
        //@1ejun6 > question status for current student & current step of grading has been running for n seconds
        GradeStatus result = snapshot.results().get(question);
        boolean active = result == null && question.equals(snapshot.question());

        String status = result == null ? (active ? snapshot.stage().name() : "waiting")
                : result == GradeStatus.completed ? "finished" : result.name().replace('_', ' ');
        Style style = result == null ? (active ? cyan : muted)
                : result == GradeStatus.completed ? green : result == GradeStatus.skipped ? amber : red;

        //@1ejun6 > Keep status beside question rather than at far edge of terminal
        int nameWidth = Math.min(10, Math.max(1, area.width() - status.length() - 4));
        String name = CharWidth.truncateWithEllipsis(safeText(question), nameWidth, CharWidth.TruncatePosition.END);
        String padding = " ".repeat(Math.max(0, nameWidth - CharWidth.of(name)));
        drawLine(frame, area, row, (active ? "> " : "  ") + name + padding + "  " + status, style);
    }

}
