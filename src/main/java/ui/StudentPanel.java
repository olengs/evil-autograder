package ui;

import static ui.UiDrawing.*;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import progress.ProgressSnapshot;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import model.GradeStatus;

final class StudentPanel { //student panel
    private StudentPanel() {
    }

    static void drawStudent(Frame frame, Rect area, ProgressSnapshot snapshot, long displayedSecond) {
        //@1ejun6 > student zip & completed student / total student & 
        drawLine(frame, area, 0, snapshot.student().isEmpty() ? "waiting for submissions" : snapshot.student(), base);
        drawLine(frame, area, 1, snapshot.studentNumber() == 0 ? snapshot.stage().name()
                : "student " + snapshot.studentNumber() + "/" + snapshot.totalStudents(), muted);

        String timing = "stage " + displayedSecond + "s elapsed";
        if (!snapshot.timeout().isZero()) timing += " / " + snapshot.timeout().toSeconds() + "s limit";
        drawLine(frame, area, 2, timing, muted);

        if (snapshot.question().isEmpty()) drawLine(frame, area, 3, snapshot.message(), cyan);
        int visibleRows = Math.max(0, area.height() - 4);
        //@1ejun6 > follow active question automatically when whole list does not fit

        int start = Math.max(0, Math.min(snapshot.questions().indexOf(snapshot.question()), snapshot.questions().size() - visibleRows));
        if (snapshot.questions().isEmpty()) drawLine(frame, area, 4, "waiting for assessment", muted);

        int end = Math.min(snapshot.questions().size(), start + visibleRows);
        for (int index = start; index < end; index++) {
            drawQuestion(frame, area, index - start + 4, snapshot.questions().get(index), snapshot);
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
