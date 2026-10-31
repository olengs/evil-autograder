package app;

import config.Config;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import ui.AutograderTui;
import utils.Logger;

public class TuiMain { //terminal entry point shared by Main and the TUI launcher
    public static void main(String[] args) {
        Path logFile = Path.of("log", "tui.log.jsonl");
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            new AutograderTui().run(config, args.length == 0 ? null : Path.of(args[0]));
        } catch (CancellationException error) {
            Logger.log(logFile, "cancelled", "grading cancelled > process & workspace cleanup finished");
            System.exit(130);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Logger.log(logFile, "interrupted", "grading interrupted");
            System.exit(130);
        } catch (Exception error) {
            Logger.log(logFile, "error", "error > " + error.getMessage());
            System.exit(1);
        }
    }
}
