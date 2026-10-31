package ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import java.util.regex.Pattern;

final class UiDrawing { //shared colors & safe > allocation-light text drawing
    static final Style base = Style.EMPTY.fg(Color.rgb(187, 194, 207)).bg(Color.rgb(40, 44, 52));
    //@1ejun6 > foreground > background
    static final Style muted = Style.EMPTY.fg(Color.rgb(156, 160, 164));
    static final Style cyan = Style.EMPTY.fg(Color.rgb(70, 217, 255));
    static final Style green = Style.EMPTY.fg(Color.rgb(152, 190, 101));
    static final Style amber = Style.EMPTY.fg(Color.rgb(236, 190, 123));
    static final Style red = Style.EMPTY.fg(Color.rgb(255, 108, 107));

    private static final Pattern CONTROLS = Pattern.compile("\\p{Cntrl}");

    private UiDrawing() {}

    static void drawLine(Frame frame, Rect area, int row, String value, Style style) {
        if (row < 0 || row >= area.height()) return; //@1ejun6 > stops if row is outside the available area
        frame.buffer().setStyle(new Rect(area.x(), area.y() + row, area.width(), 1), style);
        //@1ejun6 > applies chosen style to whole row
        frame.buffer().setString(area.x(), area.y() + row, CharWidth.truncateWithEllipsis(safeText(value), area.width(), CharWidth.TruncatePosition.END), style);
        //@1ejun6 > draws text on that row > shortening it if its too long
    }

    static String safeText(String value) { //@1ejun6 > replaces control characters with spaces to keep terminal display tidy
        return CONTROLS.matcher(value).replaceAll(" ");
        //@1ejun6 > keep user data from injecting terminal control codes
    }
}
