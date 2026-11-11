package app;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import ui.TerminalUi;
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;

// runs grading & saves scoresheet with terminal UI
public class TerminalMain {

    public static void main(String[] args) {
        int status = 0;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
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
            JsonLineLogger.getInstance().logException(Status.ERROR, "main", "terminal failed", error);
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
