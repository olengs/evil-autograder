package generation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import config.Config;

public class TesterGeneratorApp { //thin standalone entry point > the console ui should calls TesterGenerator directly instead
    public static void main(String[] args) {
        try {
            // optional first arg overrides the config file path > like app.Main
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));

            // defaults come from config > empty add-on list means "use config toggles"
            GenerationRequest request = new GenerationRequest(
                    config.getExamPdfFile(),
                    config.getResourcesDirectory(),
                    config.getGenerationOutputDirectory(),
                    List.of(),
                    "");

            GenerationResult result = new TesterGenerator(config).generate(request);
            printSummary(result);

            // non-zero exit when no tester was written at all > useful for scripts
            if (result.getWrittenCount() == 0) {
                System.exit(1);
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("tester generation error > " + error.getMessage());
            System.exit(1);
        }
    }

    private static void printSummary(GenerationResult result) {
        System.out.println("\n=== tester generation summary ===");
        for (QuestionGenerationOutcome outcome : result.getOutcomes()) {
            System.out.println("  " + outcome);
        }
        System.out.println(result);
    }
}
