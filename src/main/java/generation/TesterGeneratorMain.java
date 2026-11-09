package generation;

import utils.logging.JsonLineLogger;
import config.Config;
import generation.QuestionGenerationOutcome.GenerationStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TesterGeneratorMain {
    public static void main(String[] args) {
        int status = 1;
        try {
            JsonLineLogger.getInstance().Init(Path.of("log", "generation.log.jsonl"));
        } catch (IOException error) {
            System.err.println("cannot initialize generation log > " + error.getMessage());
            System.exit(1);
            return;
        }
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            var outcomes = new TesterGenerator(config).generate("");
            printSummary(outcomes);
            status = exitCode(outcomes);
        } catch (IOException | RuntimeException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGeneratorMain", error.toString());
            System.err.println("tester generation error > " + error.getMessage());
        } finally {
            try {
                JsonLineLogger.getInstance().flush();
            } catch (IOException error) {
                System.err.println("cannot flush generation log > " + error.getMessage());
                status = 1;
            }
        }
        System.exit(status);
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
