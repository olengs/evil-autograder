package generation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import config.Config;
import prompt.PromptBuilder;
import utils.logging.JsonLineLogger;
import validation.GeneratedTesterValidator;

class GenerationCoreTest {
    @TempDir Path directory;

    @Test
    void loadsBothPromptResourcesFromSharedFolder() {
        var spec = new QuestionSpec("Q1", "public class Q1 {}", null, List.of());
        String prompt = new PromptBuilder().build(List.of(spec), "reference", "extra instruction", false);
        assertTrue(prompt.contains("REQUESTED QUESTION IDS: Q1"));
        assertTrue(prompt.contains("extra instruction"));
        assertFalse(prompt.contains("resource not found"));
    }

    @Test
    void generationSettingsPreserveGradingWorkers() throws Exception {
        Files.writeString(directory.resolve("config.properties"), """
                input.directory=.
                output.directory=output
                submissions.folder=submissions
                resources.folder=resources
                testers.folder=testers
                scoresheet.file=grades.csv
                grading.workers=2
                compilation.timeout.seconds=30
                execution.timeout.seconds=5
                """);
        var config = new Config(directory.resolve("config.properties"), Map.of(
                "input.directory", directory.toString(), "llm.model", "fixture-model", "llm.reasoning.effort", "high",
                "llm.api.key.env", "TEST_KEY", "llm.request.timeout.seconds", "45",
                "llm.send.pdf", "false", "exam.pdf.file", "exam.pdf",
                "generation.output.dir", "generated"));
        config.validateGenerationSettings();
        assertEquals(2, config.getNumGradingWorkers());
        assertEquals(Duration.ofSeconds(45), config.getLlmRequestTimeout());
        assertEquals(directory.resolve("generated"), config.getGenerationOutputDirectory());
    }

    @Test
    void supportsMoreCasesThanMarksAndRejectsInvalidCounts() throws Exception {
        JsonLineLogger.getInstance().Init(directory.resolve("counts.jsonl"));
        var validator = new GeneratedTesterValidator(Duration.ofSeconds(30), Duration.ofSeconds(5));
        var spec = new QuestionSpec("Q1", "public class Q1 {}", null, List.of());
        String source = """
                public class Q1Tester {
                    public static void main(String[] args) {
                        System.out.println("Test 1: first");
                        System.out.println("Passed");
                        System.out.println("Test 2: second");
                        System.out.println("Failed");
                        System.out.println("1");
                    }
                }
                """;
        try {
            assertTrue(validator.validate(spec, source, 2, 1).isValid());
            assertFalse(validator.validate(spec, source, 4, 1).isValid());
            assertFalse(validator.validate(spec, source, 0, 1).isValid());
            assertFalse(validator.validate(spec, source, 3, 2).isValid());
            assertFalse(validator.validate(spec, source, 2, 0).isValid());
        } finally {
            JsonLineLogger.getInstance().flush();
        }
    }

    @Test
    void validatesGeneratedTesterAndLogsRejectedOutput() throws Exception {
        Path log = directory.resolve("generation.jsonl");
        JsonLineLogger.getInstance().Init(log);
        var validator = new GeneratedTesterValidator(Duration.ofSeconds(30), Duration.ofSeconds(5));
        var spec = new QuestionSpec("Q1", "public class Q1 {}", null, List.of());
        String source = """
                public class Q1Tester {
                    public static void main(String[] args) {
                        System.out.println("Test 1: example");
                        System.out.println("Passed");
                        System.out.println("1");
                    }
                }
                """;
        try {
            assertTrue(validator.validate(spec, source, 1).isValid());
            assertFalse(validator.validate(spec, source, 2).isValid());
        } finally {
            JsonLineLogger.getInstance().flush();
        }
        assertTrue(Files.readString(log).contains("expected 2 cases but found 1"));
    }
}
