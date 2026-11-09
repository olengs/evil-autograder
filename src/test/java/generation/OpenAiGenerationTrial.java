package generation;

import config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import utils.logging.JsonLineLogger;

/** Manual live API trial. Not discovered or executed by mvn test. */
public final class OpenAiGenerationTrial {
    private OpenAiGenerationTrial() {}

    public static void main(String[] args) throws Exception {
        Path configFile = Path.of(args.length > 0 ? args[0] : "config.properties");
        String extraPrompt = args.length > 1 ? args[1] : "";
        Path parent = Files.createDirectories(Path.of("output", "generation-trials").toAbsolutePath());
        Path output = Files.createTempDirectory(parent, "run-");
        var logger = JsonLineLogger.getInstance();
        logger.Init(output.resolve("generation.log.jsonl"));
        System.out.println("Trial output: " + output);
        try {
            // Override both supported property names so this trial cannot replace existing testers.
            var config = new Config(configFile, Map.of(
                    "generation.files.dir", output.toString(),
                    "generation.output.dir", output.toString()));
            System.out.println("Model: " + config.getLlmModel());
            System.out.println("This run sends reference material to OpenAI and uses API credits.");
            var outcomes = new TesterGenerator(config).generate(extraPrompt, new GenerationListener() {
                @Override public void started(List<String> ids) {
                    System.out.println("Questions: " + String.join(", ", ids));
                }
                @Override public void stageChanged(String message) {
                    System.out.println("> " + message);
                }
                @Override public void questionStarted(String id) {
                    System.out.println("Validating " + id);
                }
                @Override public void questionFinished(QuestionGenerationOutcome outcome) {
                    System.out.println(outcome);
                }
            });
            if (TesterGeneratorMain.exitCode(outcomes) != 0) {
                throw new IllegalStateException("Some testers were not generated successfully; inspect " + output);
            }
            System.out.println("All testers generated and validated. Output: " + output);
        } catch (Exception error) {
            logger.append(JsonLineLogger.Status.ERROR, "OpenAiGenerationTrial", error.toString());
            throw error;
        } finally {
            logger.flush();
        }
    }
}
