import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import config.Config;
import generation.TesterGenerator;
import generation.llm.LlmClient;
import generation.llm.LlmException;
import generation.llm.LlmRequest;
import generation.prompt.PrompterRegistry;
import generation.run.GenerationRequest;
import generation.run.GenerationResult;
import generation.run.GenerationStatus;
import generation.run.QuestionGenerationOutcome;
import generation.schema.GeneratedTesterSpec;
import generation.schema.GeneratedTesters;
import generation.validation.GeneratedTesterValidator;

class TesterGeneratorTest {
    @TempDir
    Path temp;

    // answers each structured call with the next reply in order > records every prompt it was sent
    private static final class StubLlmClient implements LlmClient {
        private final List<Function<String, GeneratedTesters>> replies;
        private final List<String> prompts = new ArrayList<>();

        StubLlmClient(List<Function<String, GeneratedTesters>> replies) {
            this.replies = replies;
        }

        @Override
        public String complete(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String providerName() {
            return "stub";
        }

        @Override
        public boolean supportsStructuredOutput() {
            return true;
        }

        @Override
        public <T> T completeStructured(LlmRequest request, Class<T> type) {
            prompts.add(request.getPrompt());
            return type.cast(replies.get(prompts.size() - 1).apply(request.getPrompt()));
        }
    }

    private static GeneratedTesterSpec tester(String questionId, String actual, String expected) {
        GeneratedTesterSpec.TestCase testCase = new GeneratedTesterSpec.TestCase();
        testCase.label = actual;
        testCase.setupCode = "";
        testCase.actualExpression = actual;
        testCase.expectedExpression = expected;
        GeneratedTesterSpec spec = new GeneratedTesterSpec();
        spec.questionId = questionId;
        spec.imports = List.of();
        spec.helperCode = "";
        spec.testCases = List.of(testCase);
        return spec;
    }

    private static GeneratedTesters reply(GeneratedTesterSpec... testers) {
        GeneratedTesters reply = new GeneratedTesters();
        reply.testers = List.of(testers);
        return reply;
    }

    // Q9/ holds two answer templates, a data file & a binary file
    private Path resources() throws IOException {
        Path folder = Files.createDirectories(temp.resolve("resources/Q9"));
        Files.writeString(folder.resolve("Q9a.java"), "public class Q9a { public static int add(int a, int b) { return a + b; } }");
        Files.writeString(folder.resolve("Q9b.java"), "public class Q9b { public static int twice(int a) { return a * 2; } }");
        Files.writeString(folder.resolve("data.txt"), "hello data");
        Files.write(folder.resolve("Bin.class"), new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, (byte) 0xFF });
        return temp.resolve("resources");
    }

    // the real config with the pdf upload off so no provider is needed
    private Config config() throws IOException {
        Path file = temp.resolve("config.properties");
        Files.writeString(file, Files.readString(Path.of("config.properties"))
                + "\nllm.send.pdf=false\ngeneration.max.retries=1\n");
        return new Config(file);
    }

    private GenerationResult generate(StubLlmClient llm, Path output) throws IOException {
        TesterGenerator generator = new TesterGenerator(config(), llm, new PrompterRegistry(),
                new GeneratedTesterValidator(Duration.ofSeconds(60), Duration.ofSeconds(30)));
        return generator.generate(new GenerationRequest(temp.resolve("exam.pdf"), resources(), output, List.of(), ""));
    }

    @Test
    void oneReplyCarriesEveryTesterAndRetriesOnlyTheMissingOne() throws IOException {
        Path output = temp.resolve("out");
        StubLlmClient llm = new StubLlmClient(List.of(
                prompt -> reply(tester("q9a", "add(2, 3)", "5")), // id matched case-insensitively > Q9b missing
                prompt -> reply(tester("Q9b", "twice(4)", "8"))));

        GenerationResult result = generate(llm, output);

        assertEquals(2, llm.prompts.size());
        String first = llm.prompts.get(0);
        assertTrue(first.contains("these question ids: Q9a, Q9b"), first);
        assertTrue(first.contains("Q9/\nBin.class (binary file, contents not shown)"), first);
        assertTrue(first.contains("data.txt\n```txt\nhello data\n```"), first);
        assertTrue(first.contains("Q9a.java\n```java\npublic class Q9a"), first);

        String retry = llm.prompts.get(1);
        assertTrue(retry.contains("these question ids: Q9b"), retry);
        assertTrue(retry.contains("- Q9b > no tester was returned for Q9b"), retry);

        assertEquals(List.of(GenerationStatus.WRITTEN, GenerationStatus.WRITTEN),
                result.getOutcomes().stream().map(QuestionGenerationOutcome::getStatus).toList());
        assertTrue(Files.readString(output.resolve("Q9aTester.java")).contains("public class Q9aTester extends Q9a"));
        assertTrue(Files.readString(output.resolve("Q9bTester.java")).contains("public class Q9bTester extends Q9b"));
        assertEquals(first.substring(first.indexOf("Q9/"), first.indexOf("Q9/") + 20),
                Files.readString(output.resolve("files.md")).substring(0, 20));
    }

    @Test
    void outOfAttemptsWritesInvalidTesterAndReportsMissingOne() throws IOException {
        Path output = temp.resolve("out");
        StubLlmClient llm = new StubLlmClient(List.of(
                prompt -> reply(tester("Q9a", "add(2, \"x\")", "5")), // does not compile > Q9b missing
                prompt -> {
                    throw new LlmException("quota exceeded");
                }));

        List<QuestionGenerationOutcome> outcomes = generate(llm, output).getOutcomes();

        assertEquals(GenerationStatus.VALIDATION_FAILED, outcomes.get(0).getStatus());
        assertTrue(outcomes.get(0).getWrittenFile().isPresent());
        assertTrue(outcomes.get(0).getMessage().contains("quota exceeded"), outcomes.get(0).getMessage());
        assertEquals(GenerationStatus.GENERATION_FAILED, outcomes.get(1).getStatus());
        assertTrue(Files.notExists(output.resolve("Q9bTester.java")));
    }
}
