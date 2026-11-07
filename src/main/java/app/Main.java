//starts autograder > runs grading > prints > verifies result > saves scoresheet > reports failures

package app;

import config.Config;
import grading.verification.JUnitRunner;
import java.io.IOException;
import java.nio.file.Path;
import report.ConsoleReportPrinter;
import report.ScoreSheetWriter;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));

            var results = Autograder.run(config);
            ConsoleReportPrinter.print(results);
            JUnitRunner.verify(config, results);

            Path outputScoresheet = config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName());
            ScoreSheetWriter.write(config.getScoresheetFile(), results, outputScoresheet);
            System.out.println("\nscoresheet saved > " + outputScoresheet);
        } catch (IOException | RuntimeException error) {
            System.err.println("error > " + error.getMessage());
            System.exit(1);
        }
    }
}
