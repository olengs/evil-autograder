package app;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import ui.TerminalUi;
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;

public class TerminalMain { //starts terminal UI & logs failures or cancellation

    public static void main(String[] args) {
        Path logFile = Path.of("log", "tui.log.jsonl");
        int status = 0;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            JsonLineLogger.getInstance().Init(logFile);
            new TerminalUi().run(Path.of(args.length == 0 ? "config.properties" : args[0]));
        } catch (CancellationException error) {
            JsonLineLogger.getInstance().append(Status.LOG, "main", "grading cancelled");
            status = 130;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            JsonLineLogger.getInstance().append(Status.LOG, "main", "grading cancelled");
            status = 1;
        } catch (Exception error) {
            System.err.println("error > " + error.getMessage());
            JsonLineLogger.getInstance().append(Status.ERROR, "main", "error > " + error.getMessage());
            status = 1;
        }

        try {
            JsonLineLogger.getInstance().append(Status.LOG, "main", "process & workspace cleanup finished");
            JsonLineLogger.getInstance().flush();    
        } catch (IOException e) {
            System.out.println("Failed to flush logs:" + e.getMessage());
            status = 1;
        }

        System.exit(status);
    }
}
