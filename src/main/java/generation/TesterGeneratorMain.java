package generation;

import config.Config;
import generation.QuestionGenerationOutcome.GenerationStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TesterGeneratorMain {
    public static void main(String[] args) {
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            var outcomes = new TesterGenerator(config).generate("");
            printSummary(outcomes);
            System.exit(exitCode(outcomes));
        } catch (IOException | RuntimeException error) {
            System.err.println("tester generation error > " + error.getMessage());
            System.exit(1);
        }
    }

    public static int exitCode(List<QuestionGenerationOutcome> outcomes) {
        return !outcomes.isEmpty() && count(outcomes, GenerationStatus.written) == outcomes.size() ? 0 : 1;
    }

    private static void printSummary(List<QuestionGenerationOutcome> outcomes) {
        System.out.println("\n=== tester generation summary ===");
        for (var outcome : outcomes) System.out.println("  " + outcome);
        System.out.printf("total=%d, written=%d, validationFailed=%d, generationFailed=%d%n",
                outcomes.size(), count(outcomes, GenerationStatus.written),
                count(outcomes, GenerationStatus.validationFailed), count(outcomes, GenerationStatus.generationFailed));
    }

    private static long count(List<QuestionGenerationOutcome> outcomes, GenerationStatus status) {
        return outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }
}
