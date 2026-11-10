package ui;

import config.SetupConfigChecker;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.event.TickEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

// Code mostly from https://github.com/tamboui/tamboui
// main TUI logic, control flow and keyboard input
// setup > menu > generation or grading > summary > menu
public class TerminalUi { 
    private UiSession session;
    private GradingView gradingView;
    private ResourceUsage.Sample lastRenderedResources;
    private int selected;
    private String setupMessage = "";

    public void run(Path configFile) throws Exception {
        SetupConfigChecker setup = new SetupConfigChecker(configFile);
        StringBuilder input = new StringBuilder();
        ResourceUsage resources = new ResourceUsage();
        session = null;
        gradingView = null;
        lastRenderedResources = null;
        selected = 0;
        setupMessage = "";
        if (setup.issue() == null) session = new UiSession(setup.config());

        // Keep one terminal open; each menu action starts a fresh background worker.
        TuiConfig terminalConfig = TuiConfig.builder().tickRate(Duration.ofMillis(250)).build();
        try (TuiRunner terminal = TuiRunner.create(terminalConfig)) {
            try {
                terminal.run((event, runner) -> {
                    if (event instanceof KeyEvent key && key.isCtrlC()) {
                        runner.quit();
                        return true;
                    }
                    // Setup accepts replacement paths or an empty Enter to retry edited settings.
                    if (session == null) {
                        if (event instanceof PasteEvent paste) { input.append(paste.text()); return true; }
                        if (!(event instanceof KeyEvent key)) return false;
                        if (key.code() == KeyCode.ENTER) {
                            try {
                                setup.replace(input.toString());
                                input.setLength(0);
                                setupMessage = "";
                                if (setup.issue() == null) session = new UiSession(setup.config());
                            } catch (IOException error) { setupMessage = error.getMessage(); }
                        } else if (key.code() == KeyCode.BACKSPACE && !input.isEmpty())
                            input.delete(input.offsetByCodePoints(input.length(), -1), input.length());
                        else if (key.code() == KeyCode.CHAR && !Character.isISOControl(key.codePoint())) input.append(key.string());
                        return true;
                    }
                    // Settle a task only after its thread exits, including process/workspace cleanup.
                    if (event instanceof TickEvent) {
                        boolean wasSettled = session.settled();
                        session.poll();
                        // Active work needs animation; idle screens redraw only when displayed metrics change.
                        return !session.settled() || !wasSettled || !resources.sample().equals(lastRenderedResources);
                    }
                    if (!(event instanceof KeyEvent key)) return false;
                    if (session.screen() == UiSession.Screen.menu) {
                        if (key.isChar('q') || key.isChar('3')) { runner.quit(); return true; }
                        if (key.code() == KeyCode.UP) selected = (selected + 2) % 3;
                        else if (key.code() == KeyCode.DOWN) selected = (selected + 1) % 3;
                        else if (key.isChar('1') || key.isChar('g')) { selected = 0; start(); }
                        else if (key.isChar('2') || key.isChar('r')) { selected = 1; start(); }
                        else if (key.code() == KeyCode.ENTER) {
                            if (selected == 2) runner.quit(); else start();
                        }
                        return true;
                    }
                    session.poll();
                    // Finished summaries remain visible until Enter; q cancels active work.
                    if (session.settled() && (key.code() == KeyCode.ENTER || key.isChar('q'))) session.menu();
                    else if (key.isChar('q')) session.cancel();
                    return true;
                }, 
                frame -> {
                    ResourceUsage.Sample resourceSample = resources.sample();
                    lastRenderedResources = resourceSample;
                    if (session == null) {
                        var issue = setup.issue();
                        if (!setupMessage.isEmpty()) issue = new SetupConfigChecker.Issue(issue.setting(), issue.label(), issue.path(), setupMessage);
                        SetupScreen.draw(frame, issue, input.toString());
                    } else if (session.screen() == UiSession.Screen.menu) {
                        SessionScreen.menu(frame, session, selected, resourceSample);
                    } else {
                        String commands = session.settled() ? "[enter] menu" : session.cancelling() ? "cancelling > waiting for cleanup" : "[q] cancel  [ctrl+c] quit";
                        if (session.screen() == UiSession.Screen.generating)
                            SessionScreen.generation(frame, session.generation().snapshot(), commands, resourceSample);
                        else {
                            gradingView.update(session.grading().getSnapshot());
                            gradingView.updateResources(resourceSample);
                            gradingView.render(frame, commands);
                        }
                    }
                });
            } finally {
                // Closing the application also waits for API/process cleanup before releasing the terminal.
                if (session != null) session.close();
            }
        }
    }

    private void start() {
        session.start(selected == 0 ? UiSession.Screen.generating : UiSession.Screen.grading);
        if (session.screen() == UiSession.Screen.grading)
            gradingView = new GradingView(session.config().getInputDirectory(), session.grading().getSnapshot());
    }
}
