package generation;

import java.io.IOException;
import java.nio.file.Path;

import config.Config;
import generation.run.GenerationRequest;
import generation.run.GenerationResult;
import generation.run.QuestionGenerationOutcome;

public class TesterGeneratorMain { //thin standalone entry point
    public static void main(String[] args) {
        try {
            //optional first arg overrides config file path > like app.Main
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));

            //defaults come from config
            GenerationRequest request = new GenerationRequest(config.getExamPdfFile(), config.getResourcesDirectory(), config.getGenerationOutputDirectory(), "");

            GenerationResult result = new TesterGenerator(config).generate(request);
            printSummary(result);

            System.exit(exitCode(result));
        } catch (IOException | RuntimeException error) {
            System.err.println("tester generation error > " + error.getMessage());
            System.exit(1);
        }
    }

    public static int exitCode(GenerationResult result) {
        return result.isFullySuccessful() ? 0 : 1;
    }

    private static void printSummary(GenerationResult result) {
        System.out.println("\n=== tester generation summary ===");
        for (QuestionGenerationOutcome outcome : result.getOutcomes()) {
            System.out.println("  " + outcome);
        }
        System.out.println(result);
    }
}
