//shares terminal colors & bordered panels & safe text rendering
//clips displayed text to available cells & removes control sequences

package ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import java.util.regex.Pattern;

public final class UiDrawing {
    public static final Style base = Style.EMPTY.fg(Color.rgb(187, 194, 207)).bg(Color.rgb(40, 44, 52));
    //foreground > background
    public static final Style muted = Style.EMPTY.fg(Color.rgb(156, 160, 164));
    public static final Style cyan = Style.EMPTY.fg(Color.rgb(70, 217, 255));
    public static final Style green = Style.EMPTY.fg(Color.rgb(152, 190, 101));
    public static final Style amber = Style.EMPTY.fg(Color.rgb(236, 190, 123));
    public static final Style red = Style.EMPTY.fg(Color.rgb(255, 108, 107));

    private static final Pattern CONTROLS = Pattern.compile("\\p{Cntrl}");

    private UiDrawing() {}

    public static Rect box(Frame frame, Rect area, String title) {
        Block block = Block.builder().borders(Borders.ALL).title(" " + title + " ").borderStyle(muted).build();
        frame.renderWidget(block, area);
        return block.inner(area);
    }

    public static void drawLine(Frame frame, Rect area, int row, String value, Style style) {
        if (row < 0 || row >= area.height()) return; //stops if row is outside the available area
        frame.buffer().setStyle(new Rect(area.x(), area.y() + row, area.width(), 1), style);
        //applies chosen style to whole row
        frame.buffer().setString(area.x(), area.y() + row, CharWidth.truncateWithEllipsis(safeText(value), area.width(), CharWidth.TruncatePosition.END), style);
        //draws text on that row > shortening it if its too long
    }

    public static String safeText(String value) { //replaces control characters with spaces to keep terminal display tidy
        return CONTROLS.matcher(value).replaceAll(" ");
        //keep user data from injecting terminal control codes
    }
}
