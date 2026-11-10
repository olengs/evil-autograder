package app;

import java.io.IOException;
import java.nio.file.Path;

import config.Config;
import grading.progress.GradingProgress;
import report.ConsoleReportPrinter;
import utils.logging.JsonLineLogger;

public final class Main { //runs console grading & saves scoresheet
    private Main() {}

    public static void main(String[] args) {
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            JsonLineLogger.getInstance().Init(Path.of("log", "console.log.jsonl"), config.getLogBufferThreshold());
            try {
                var results = GradingWorkflow.run(config, new GradingProgress());
                ConsoleReportPrinter.print(results);
            } finally {
                JsonLineLogger.getInstance().flush();
            }

            Path outputScoresheet = config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName());
            System.out.println("\nscoresheet saved > " + outputScoresheet);
        } catch (IOException | RuntimeException error) {
            System.err.println("error > " + error.getMessage());
            System.exit(1);
        }
    }
}
