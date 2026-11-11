package app;

import java.io.IOException;
import java.nio.file.Path;

import config.Config;
import grading.progress.GradingProgress;
import report.ConsoleReportPrinter;
import utils.logging.JsonLineLogger;

// runs grading & saves scoresheet on base console without ui
public final class Main { 
    private Main() {}

    public static void main(String[] args) {
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            JsonLineLogger.getInstance().Init(config.getLogFile(), config.getLogBufferThreshold(), config.getLogLevels());
            try {
                var results = GradingWorkflow.run(config, new GradingProgress());
                ConsoleReportPrinter.print(results);
            } finally {
                JsonLineLogger.getInstance().flush();
            }

            Path outputScoresheet = config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName());
            System.out.println("\nscoresheet saved > " + outputScoresheet);
        } catch (IOException | RuntimeException error) {
            JsonLineLogger.getInstance().logException(JsonLineLogger.Status.ERROR, "Main", "grading failed", error);
            System.err.println("error > " + (error.getMessage() == null ? "Grading failed; see log" : error.getMessage()));
            System.exit(1);
        }
    }
}
