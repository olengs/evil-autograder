package ui;

import static utils.ui.UiDrawing.*;
import config.Config;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import java.util.regex.Pattern;
import dev.tamboui.widgets.paragraph.Paragraph;
import generation.GenerationListener;
import generation.QuestionGenerationOutcome;
import generation.QuestionGenerationOutcome.GenerationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenerationView implements GenerationListener {
    private final StringBuilder[] inputs;
    private final boolean sendPdf;
    private final String model;
    private final long requestTimeout;
    private final String apiKeyEnv;
    private int field;
    private int scroll;
    private int maxScroll;
    private String formError = "";
    private List<String> questions = List.of();
    private final Map<String, QuestionGenerationOutcome> outcomes = new LinkedHashMap<>();
    private String message = "preparing generation";
    private String error = "";
    private boolean finished;
    private long started;
    private long ended;
    private String currentQuestion = "";

    private record Snapshot(List<String> questions, Map<String, QuestionGenerationOutcome> outcomes,
            String message, String error, String currentQuestion, boolean finished,
            long elapsedSeconds, int activityFrame) {}

    public GenerationView(Config config) {
        this(config, "");
    }

    GenerationView(Config config, String instructions) {
        inputs = new StringBuilder[]{new StringBuilder(config.getExamPdfFile().toString()),
                new StringBuilder(config.getGenerationOutputDirectory().toString()), new StringBuilder(instructions)};
        sendPdf = config.isLlmSendPdf();
        model = config.getLlmModel() + " / " + config.getLlmReasoningEffort();
        requestTimeout = config.getLlmRequestTimeout().toSeconds();
        apiKeyEnv = config.getLlmApiKeyEnv();
        field = -1;
    }

    String pdfPath() { return inputs[0].toString(); }
    String outputPath() { return inputs[1].toString(); }
    String instructions() { return inputs[2].toString(); }
    void paths(Config config) {
        inputs[0].replace(0, inputs[0].length(), config.getExamPdfFile().toString());
        inputs[1].replace(0, inputs[1].length(), config.getGenerationOutputDirectory().toString());
    }
    void formError(String value) { formError = value; }
    boolean editing() { return field >= 0; }
    void finishEditing() { field = -1; }
    void append(String text) { if (editing()) inputs[field].append(text); }

    void edit(KeyEvent key) {
        if (!editing()) {
            if (key.isChar('1')) field = 2;
            else if (key.isChar('2') && sendPdf) field = 0;
            else if (key.isChar('3')) field = 1;
            else if (key.code() == KeyCode.TAB || key.code() == KeyCode.DOWN || key.code() == KeyCode.UP) field = 2;
            return;
        }
        if (key.code() == KeyCode.TAB || key.code() == KeyCode.DOWN || key.code() == KeyCode.UP) {
            field = (field + (key.code() == KeyCode.UP ? inputs.length - 1 : 1)) % inputs.length;
            if (!sendPdf && field == 0) field = 1;
        } else if (key.code() == KeyCode.BACKSPACE && !inputs[field].isEmpty()) {
            var input = inputs[field];
            input.delete(input.offsetByCodePoints(input.length(), -1), input.length());
        } else if (key.code() == KeyCode.CHAR && !Character.isISOControl(key.codePoint())) append(key.string());
    }

    synchronized void begin() {
        started = System.nanoTime();
        ended = 0;
        finished = false;
        error = "";
        message = "loading generation settings";
        questions = List.of();
        outcomes.clear();
        scroll = 0;
        currentQuestion = "";
    }

    @Override public synchronized void started(List<String> ids) { questions = List.copyOf(ids); }
    @Override public synchronized void stageChanged(String value) { message = value; }
    @Override public synchronized void questionStarted(String id) { currentQuestion = id; stageChanged("validating and saving " + id); }
    @Override public synchronized void questionFinished(QuestionGenerationOutcome outcome) { outcomes.put(outcome.questionId(), outcome); }

    synchronized void finish(List<QuestionGenerationOutcome> results, String failure) {
        if (questions.isEmpty()) questions = results.stream().map(QuestionGenerationOutcome::questionId).toList();
        results.forEach(this::questionFinished);
        error = failure == null ? "" : failure;
        finished = true;
        ended = System.nanoTime();
        message = canGrade() ? "all testers validated" : "generation needs attention";
        currentQuestion = "";
    }

    synchronized void fail(String failure) { finish(results(), failure); }

    synchronized List<QuestionGenerationOutcome> results() {
        return questions.stream().filter(outcomes::containsKey).map(outcomes::get).toList();
    }

    synchronized boolean canGrade() {
        return finished && error.isEmpty() && !questions.isEmpty() && questions.size() == outcomes.size()
                && questions.stream().allMatch(id -> outcomes.containsKey(id) && outcomes.get(id).status() == GenerationStatus.written);
    }

    private synchronized Snapshot snapshot() {
        long now = finished ? ended : System.nanoTime();
        long elapsed = started == 0 ? 0 : Math.max(0, now - started);
        return new Snapshot(List.copyOf(questions), Map.copyOf(outcomes), message, error, currentQuestion, finished,
                elapsed / 1_000_000_000L, (int) (elapsed / 250_000_000L % 4));
    }

    void scroll(int delta) { scroll = Math.max(0, Math.min(maxScroll, scroll + delta)); }

    void render(Frame frame, boolean form, boolean cancelling, ResourceUsage.Sample resources) {
        frame.buffer().fill(frame.area(), new Cell(" ", base));
        int minimumHeight = 34;
        if (frame.width() < 32 || frame.height() < minimumHeight) {
            frame.renderWidget(Paragraph.builder().text("terminal too small\nresize to at least 32 x " + minimumHeight).style(base).build(), frame.area());
            return;
        }
        int width = Math.min(frame.width() - 2, 96);
        int height = Math.min(frame.height() - 2, form ? 38 : 36);
        Rect inner = box(frame, new Rect((frame.width() - width) / 2, 1, width, height), "evil autograder");
        Rect area = new Rect(inner.x() + 1, inner.y(), inner.width() - 2, inner.height());
        if (form) drawForm(frame, area);
        else drawProgress(frame, area, cancelling, resources);
    }

    private void drawForm(Frame frame, Rect area) {
        drawLine(frame, area, 0, "setup > generate testers with openai", muted);
        Rect settings = box(frame, new Rect(area.x(), area.y() + 2, area.width(), 4), "openai");
        drawLine(frame, settings, 0, "model > " + model, muted);
        drawLine(frame, settings, 1, "key > " + apiKeyEnv + " in environment or .env", muted);
        String[] labels = {sendPdf ? "[2] exam pdf" : "exam pdf (not attached)", "[3] save generated testers to", "[1] optional instructions"};
        int[] positions = {7, 11, 15};
        for (int i = 0; i < inputs.length; i++) {
            Rect input = box(frame, new Rect(area.x(), area.y() + positions[i], area.width(), i == 2 && (field == 2 || !inputs[2].isEmpty()) ? 5 : 3),
                    (i == field ? "> " : "") + labels[i]);
            if (i == 2) {
                String value = safeText(inputs[i].toString()) + (i == field ? "_" : "");
                int rows = Math.max(1, (CharWidth.of(value) + input.width() - 1) / input.width());
                frame.renderWidget(Paragraph.builder().text(value).style(i == field ? cyan : muted)
                        .overflow(Overflow.WRAP_CHARACTER).scroll(Math.max(0, rows - input.height())).build(), input);
            } else {
                drawLine(frame, input, 0, CharWidth.truncateWithEllipsis(safeText(inputs[i].toString()) + (i == field ? "_" : ""),
                        input.width(), CharWidth.TruncatePosition.START), i == field ? cyan : base);
            }
        }
        int commandY = area.y() + area.height() - 3;
        if (!formError.isEmpty()) {
            frame.renderWidget(Paragraph.builder().text(safeText(formError)).style(red).overflow(Overflow.WRAP_WORD).build(),
                    new Rect(area.x(), area.y() + 21, area.width(), Math.max(1, commandY - area.y() - 21)));
        }
        Rect commands = box(frame, new Rect(area.x(), commandY, area.width(), 3), "commands");
        String text = editing() ? "[enter] done   [ctrl+c] close"
                : "[1|2|3] edit   [enter] generate   [ctrl+c] close";
        if (CharWidth.of(text) > commands.width()) text = editing()
                ? "[enter] done   [ctrl+c]" : "[1|2|3]   [enter]   [ctrl+c]";
        drawLine(frame, commands, 0, text, cyan);
    }

    private void drawProgress(Frame frame, Rect area, boolean cancelling, ResourceUsage.Sample resources) {
        Snapshot state = snapshot();
        drawLine(frame, area, 0, state.finished() ? "generation > review testers" : "generation > openai", muted);
        int bodyHeight = area.height() - 15;
        boolean columns = area.width() >= 65;
        Rect activity;
        Rect body;
        if (columns) {
            int leftWidth = (area.width() - 1) * 2 / 5;
            activity = box(frame, new Rect(area.x(), area.y() + 2, leftWidth, bodyHeight), "openai generation");
            body = box(frame, new Rect(area.x() + leftWidth + 1, area.y() + 2, area.width() - leftWidth - 1, bodyHeight), "testers / results");
        } else {
            activity = box(frame, new Rect(area.x(), area.y() + 2, area.width(), 5), "openai generation");
            body = box(frame, new Rect(area.x(), area.y() + 7, area.width(), bodyHeight - 5), "testers / results");
        }
        drawActivity(frame, activity, state, cancelling, columns && activity.height() >= 6);
        drawResults(frame, body, state);
        int footerY = area.y() + 3 + bodyHeight;
        Rect folder = box(frame, new Rect(area.x(), footerY, area.width(), 3), "tester folder");
        drawLine(frame, folder, 0, CharWidth.truncateWithEllipsis(safeText(outputPath()), folder.width(), CharWidth.TruncatePosition.START), base);
        Rect usage = box(frame, new Rect(area.x(), footerY + 4, area.width(), 4), "resource usage");
        drawLine(frame, usage, 0, resources.summary(), muted);
        drawLine(frame, usage, 1, "threads > " + resources.threads(), muted);
        Rect commands = box(frame, new Rect(area.x(), footerY + 9, area.width(), 3), "commands");
        String commandText = "[ctrl+c] close";
        if (state.finished() && !cancelling) {
            commandText = (canGrade() ? "[enter] grade with these testers" : "[r] regenerate")
                    + "   [b] back   [ctrl+c] close";
            if (CharWidth.of(commandText) > commands.width()) {
                commandText = (canGrade() ? "[enter] grade" : "[r] retry") + "   [b] back   [ctrl+c] close";
            }
            if (CharWidth.of(commandText) > commands.width()) {
                commandText = (canGrade() ? "[enter]" : "[r]") + "   [b]   [ctrl+c]";
            }
        }
        drawLine(frame, commands, 0, commandText, cyan);
    }

    private record ResultLine(String text, Style style) {}
    private static final Pattern caseCount = Pattern.compile("^validated (\\d+) cases & final score$");

    private void drawResults(Frame frame, Rect body, Snapshot state) {
        long validated = state.outcomes().values().stream().filter(o -> o.status() == GenerationStatus.written).count();
        long failed = state.outcomes().values().stream().filter(o -> o.status() != GenerationStatus.written).count();
        drawLine(frame, body, 0, validated + "/" + state.questions().size() + " validated" + (failed > 0 ? " | " + failed + " failed" : ""),
                failed > 0 ? amber : state.finished() && canGrade() ? green : muted);
        boolean showCases = body.width() >= 30;
        int available = body.width() - (showCases ? 4 : 3);
        int nameWidth = Math.max(5, available * 28 / 100);
        int countWidth = showCases ? Math.max(5, available * 20 / 100) : 0;
        int statusWidth = available - nameWidth - countWidth;
        boolean header = body.height() >= 7;
        if (header) {
            drawLine(frame, body, 2, gridBorder(nameWidth, statusWidth, countWidth, "┌", "┬", "┐"), muted);
            drawLine(frame, body, 3, gridRow("question", "status", "cases", nameWidth, statusWidth, countWidth), muted);
            drawLine(frame, body, 4, gridBorder(nameWidth, statusWidth, countWidth, "├", "┼", "┤"), muted);
        }
        List<ResultLine> lines = new ArrayList<>();
        for (String id : state.questions()) {
            var outcome = state.outcomes().get(id);
            String status;
            Style style;
            String count = "-";
            if (outcome == null) {
                boolean active = id.equals(state.currentQuestion());
                status = state.finished() ? "not returned" : active ? "validating" : "waiting";
                style = state.finished() ? red : active ? cyan : muted;
            } else if (outcome.status() == GenerationStatus.written) {
                status = "validated";
                style = green;
                var match = caseCount.matcher(outcome.message());
                if (match.matches()) count = match.group(1);
            } else {
                status = "failed";
                style = red;
            }
            lines.add(new ResultLine(gridRow(id, status, count, nameWidth, statusWidth, countWidth), style));
            boolean last = id.equals(state.questions().getLast());
            lines.add(new ResultLine(gridBorder(nameWidth, statusWidth, countWidth,
                    last ? "└" : "├", last ? "┴" : "┼", last ? "┘" : "┤"), muted));
        }
        if (state.questions().isEmpty() && header) {
            lines.add(new ResultLine(gridBorder(nameWidth, statusWidth, countWidth, "└", "┴", "┘"), muted));
        }
        if (!state.error().isEmpty()) addDetail(lines, state.error(), body.width(), red);
        for (var outcome : state.outcomes().values()) {
            if (outcome.status() != GenerationStatus.written && !outcome.message().isBlank()) {
                addDetail(lines, outcome.questionId() + " > " + outcome.message(), body.width(), red);
            }
        }
        int firstRow = header ? 5 : 1;
        int visible = Math.max(0, body.height() - firstRow);
        maxScroll = Math.max(0, lines.size() - visible);
        scroll = Math.min(scroll, maxScroll);
        for (int row = 0; row < visible && scroll + row < lines.size(); row++) {
            ResultLine line = lines.get(scroll + row);
            drawLine(frame, body, row + firstRow, line.text(), line.style());
            if (line.text().startsWith("│")) {
                for (int offset : countWidth > 0 ? new int[]{0, nameWidth + 1, nameWidth + statusWidth + 2, body.width() - 1}
                        : new int[]{0, nameWidth + 1, body.width() - 1}) {
                    frame.buffer().setString(body.x() + offset, body.y() + row + firstRow, "│", muted);
                }
            }
        }
    }

    private static String gridBorder(int question, int status, int cases, String left, String middle, String right) {
        return left + "─".repeat(question) + middle + "─".repeat(status)
                + (cases > 0 ? middle + "─".repeat(cases) : "") + right;
    }

    private static String gridCell(String value, int width, boolean centered) {
        int contentWidth = Math.max(1, width - 2);
        String text = CharWidth.truncateWithEllipsis(safeText(value), contentWidth, CharWidth.TruncatePosition.END);
        int remaining = width - CharWidth.of(text);
        int left = centered ? remaining / 2 : 1;
        return " ".repeat(left) + text + " ".repeat(Math.max(0, remaining - left));
    }

    private static String gridRow(String question, String status, String cases, int questionWidth, int statusWidth, int countWidth) {
        return "│" + gridCell(question, questionWidth, false) + "│" + gridCell(status, statusWidth, false)
                + (countWidth > 0 ? "│" + gridCell(cases, countWidth, true) : "") + "│";
    }

    private static void addDetail(List<ResultLine> lines, String value, int width, Style style) {
        String remaining = safeText(value);
        while (!remaining.isEmpty()) {
            String chunk = CharWidth.truncateWithEllipsis(remaining, width, CharWidth.TruncatePosition.END);
            // Split without an ellipsis so the following row preserves every character.
            int end = remaining.length();
            if (CharWidth.of(remaining) > width) {
                end = 0;
                int cells = 0;
                while (end < remaining.length()) {
                    int next = end + Character.charCount(remaining.codePointAt(end));
                    int size = CharWidth.of(remaining.substring(end, next));
                    if (cells + size > width) break;
                    cells += size;
                    end = next;
                }
                if (end == 0) end = Character.charCount(remaining.codePointAt(0));
                chunk = remaining.substring(0, end);
            }
            lines.add(new ResultLine(chunk, style));
            remaining = remaining.substring(end);
        }
    }

    private void drawActivity(Frame frame, Rect area, Snapshot state, boolean cancelling, boolean expanded) {
        String indicator = state.finished() && !cancelling ? "" : "|/-\\".charAt(state.activityFrame()) + " ";
        drawLine(frame, area, 0, indicator + (cancelling ? "cancelling > waiting for cleanup" : state.message()), cancelling ? amber : cyan);
        if (!expanded) {
            drawLine(frame, area, 1, "elapsed " + state.elapsedSeconds() + "s | timeout " + requestTimeout + "s", muted);
            drawLine(frame, area, 2, "model > " + model, muted);
            return;
        }
        drawLine(frame, area, 1, "model > " + model, muted);
        drawLine(frame, area, 2, "elapsed > " + state.elapsedSeconds() + "s", muted);
        drawLine(frame, area, 3, "request timeout > " + requestTimeout + "s", muted);
        String detail = cancelling ? "Waiting for the request or process to stop and cleanup to finish."
                : state.finished() ? ""
                : state.message().equals("waiting for OpenAI") ? ""
                : state.message().equals("uploading exam PDF") ? "Attaching the exam paper to this request."
                : state.message().startsWith("validating and saving") ? "Compiling and running this tester against its answer template."
                : "Preparing questions, reference files and generated testers.";
        frame.renderWidget(Paragraph.builder().text(detail).style(muted).overflow(Overflow.WRAP_WORD).build(),
                new Rect(area.x(), area.y() + 4, area.width(), area.height() - 4));
    }
}
