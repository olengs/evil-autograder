package app;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import ui.AutograderTui;
import utils.Logger;

public class TuiMain { //terminal entry point shared by Main and the TUI launcher
    public static void main(String[] args) {
        Path logFile = Path.of("log", "tui.log.jsonl");
        try {
            new AutograderTui().run(Path.of(args.length == 0 ? "config.properties" : args[0]));
        } catch (CancellationException error) {
            Logger.log(logFile, "cancelled", "grading cancelled > process & workspace cleanup finished");
            System.exit(130);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Logger.log(logFile, "interrupted", "grading interrupted");
            System.exit(130);
        } catch (Exception error) {
            System.err.println("error > " + error.getMessage());
            Logger.log(logFile, "error", "error > " + error.getMessage());
            System.exit(1);
        }
    }
}
