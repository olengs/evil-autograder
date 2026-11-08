package app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;
import ui.TerminalUi;
import utils.logging.JsonLineLogger;

public class TerminalMain { //starts terminal UI & logs failures or cancellation
    private static void log(Path file, String status, String message) {
        try {
            JsonLineLogger.append(file, Map.of("status", status, "message", message));
        } catch (IOException error) {
            System.err.println(message);
            System.err.println("could not write " + file + " > " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Path logFile = Path.of("log", "tui.log.jsonl");
        try {
            new TerminalUi().run(Path.of(args.length == 0 ? "config.properties" : args[0]));
        } catch (CancellationException error) {
            log(logFile, "cancelled", "run cancelled > request process & workspace cleanup finished");
            System.exit(130);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log(logFile, "interrupted", "run interrupted");
            System.exit(130);
        } catch (Exception error) {
            System.err.println("error > " + error.getMessage());
            log(logFile, "error", "error > " + error.getMessage());
            System.exit(1);
        }
    }
}
