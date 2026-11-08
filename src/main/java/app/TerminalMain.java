package app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import ui.TerminalUi;
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;

public class TerminalMain { //starts terminal UI & logs failures or cancellation
    private static void log(Status status, String message) {
        try {
            JsonLineLogger.getInstance().append(status, "main", message);
        } catch (IOException error) {
            System.err.println(message);
            System.err.println("failed to log " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Path logFile = Path.of("log", "tui.log.jsonl");
        int status = 0;
        try {
            JsonLineLogger.getInstance().Init(logFile);
            new TerminalUi().run(Path.of(args.length == 0 ? "config.properties" : args[0]));
        } catch (CancellationException error) {
            log(Status.LOG, "grading cancelled");
            status = 130;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log(Status.LOG, "grading interrupted");
            status = 1;
        } catch (Exception error) {
            System.err.println("error > " + error.getMessage());
            log(Status.ERROR, "error > " + error.getMessage());
            status = 1;
        }

        try {
            log(Status.LOG, "process & workspace cleanup finished");
            JsonLineLogger.getInstance().flush();    
        } catch (IOException e) {
            System.out.println("Failed to flush logs:" + e.getMessage());
            status = 1;
        }

        System.exit(status);
    }
}
