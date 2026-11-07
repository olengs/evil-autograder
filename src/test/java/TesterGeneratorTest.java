import static org.junit.jupiter.api.Assertions.*;

import config.Config;
import generation.TesterGenerator;
import generation.llm.LlmClient;
import generation.llm.LlmException;
import generation.llm.LlmRequest;
import generation.llm.UploadedDocument;
import generation.run.GenerationRequest;
import generation.run.GenerationStatus;
import generation.schema.GeneratedTesters;
import generation.validation.GeneratedTesterValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesterGeneratorTest {
    @TempDir Path dir;
    private static final List<String> IDS = List.of("Q1a", "Q1b", "Q1c", "Q2", "Q3");

    @Test
    void generatesEveryFolderAndSubpartInOneCallAndValidatesEachTester() throws IOException {
        RecordingClient client = new RecordingClient();
        for (String id : IDS) {
            var tester = new GeneratedTesters.Tester();
            tester.id = id;
            tester.marks = 1;
            tester.issue = "";
            tester.testCode = """
                    public class %sTester extends %s {
                        public static void main(String[] args) {
                            System.out.println("Test 1: answer()");
                            boolean passed = answer() == 42;
                            System.out.println(passed ? "Passed" : "Failed");
                            System.out.println(passed ? 1 : 0);
                        }
                    }
                    """.formatted(id, id);
            client.reply.tests.add(tester);
        }

        var result = generator(client).generate(request());

        assertEquals(1, client.calls);
        assertEquals(1, client.uploads);
        assertEquals(1, client.deletions);
        assertEquals("gpt-6.1-terra", client.request.getModel());
        assertEquals(client.document, client.request.getDocument());
        assertTrue(result.isFullySuccessful());
        assertEquals(0, generation.TesterGeneratorApp.exitCode(result));
        assertEquals(IDS, result.getOutcomes().stream().map(o -> o.getQuestionId()).toList());
        for (String id : IDS) {
            assertTrue(client.request.getPrompt().contains(id + ".java"));
            assertTrue(Files.isRegularFile(dir.resolve("output/" + id + "Tester.java")));
        }
        assertTrue(Files.readString(dir.resolve("output/context/all.md")).contains("Q3.java"));
        assertTrue(Files.isRegularFile(dir.resolve("output/response/all.json")));
    }

    @Test
    void missingTestersAndTransportFailuresDoNotTriggerAnotherGenerationCall() throws IOException {
        for (boolean fail : List.of(false, true)) {
            RecordingClient client = new RecordingClient();
            client.fail = fail;

            var result = generator(client).generate(request());

            assertEquals(1, client.calls);
            assertEquals(1, client.deletions);
            assertEquals(IDS.size(), result.getOutcomes().size());
            assertTrue(result.getOutcomes().stream()
                    .allMatch(o -> o.getStatus() == GenerationStatus.GENERATION_FAILED));
        }
    }

    @Test
    void rejectsUnknownAndDuplicateIdsBeforeWritingAnyTester() throws IOException {
        for (boolean duplicate : List.of(false, true)) {
            RecordingClient client = new RecordingClient();
            client.reply.tests.add(entry("Q1a", 1, "", "invalid Java"));
            client.reply.tests.add(entry(duplicate ? "Q1a" : "Q99", 1, "", "invalid Java"));
            var result = generator(client).generate(request());
            assertEquals(1, client.calls);
            assertTrue(result.getOutcomes().stream().allMatch(o -> o.getStatus() == GenerationStatus.GENERATION_FAILED));
            assertTrue(Files.readString(dir.resolve("output/response/all.json")).contains(duplicate ? "Q1a" : "Q99"));
            assertFalse(Files.exists(dir.resolve("output/Q1aTester.java")));
        }
    }

    @Test
    void flagsMissingInformationQuarantinesInvalidCodeAndArchivesStaleTesters() throws IOException {
        var request = request();
        Files.createDirectories(request.getOutputDirectory());
        Path previous = request.getOutputDirectory().resolve("Q1aTester.java");
        Files.writeString(previous, "previous tester");
        RecordingClient client = new RecordingClient();
        client.reply.tests.add(entry("Q1a", 0, "mark allocation missing", ""));
        client.reply.tests.add(entry("Q1b", 1, "", "not Java"));
        client.reply.tests.add(entry("Q2", 1, "", """
                public class Q2Tester extends Q2 {
                    public static void main(String[] args) {
                        System.out.println("Test 1: answer");
                        System.out.println(answer() == 42 ? "Passed" : "Failed");
                        System.out.println(answer() == 42 ? 1 : 0);
                    }
                }
                """));
        var result = generator(client).generate(request);
        assertEquals(GenerationStatus.GENERATION_FAILED, result.getOutcomes().get(0).getStatus());
        assertEquals("mark allocation missing", result.getOutcomes().get(0).getMessage());
        assertEquals(GenerationStatus.VALIDATION_FAILED, result.getOutcomes().get(1).getStatus());
        assertTrue(result.getOutcomes().get(1).getWrittenFile().orElseThrow().startsWith(dir.resolve("output/review")));
        assertEquals(GenerationStatus.GENERATION_FAILED, result.getOutcomes().get(2).getStatus());
        assertEquals(GenerationStatus.WRITTEN, result.getOutcomes().get(3).getStatus());
        assertEquals(1, generation.TesterGeneratorApp.exitCode(result));
        assertFalse(Files.exists(previous));
        assertFalse(Files.exists(dir.resolve("output/Q1bTester.java")));
        assertEquals(1, client.calls);
    }

    private static GeneratedTesters.Tester entry(String id, int marks, String issue, String source) {
        var entry = new GeneratedTesters.Tester();
        entry.id = id;
        entry.marks = marks;
        entry.issue = issue;
        entry.testCode = source;
        return entry;
    }

    private TesterGenerator generator(RecordingClient client) throws IOException {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("config.properties"))) {
            props.load(reader);
        }
        props.setProperty("generation.max.retries", "0");
        props.setProperty("llm.send.pdf", "true");
        Path configPath = dir.resolve("config.properties");
        try (var writer = Files.newBufferedWriter(configPath)) {
            props.store(writer, null);
        }
        return new TesterGenerator(new Config(configPath), client,
                new GeneratedTesterValidator(Duration.ofSeconds(30), Duration.ofSeconds(10)));
    }

    private GenerationRequest request() throws IOException {
        Path resources = dir.resolve("resources");
        for (String id : IDS) {
            Path folder = Files.createDirectories(resources.resolve(id.startsWith("Q1") ? "Q1" : id));
            Files.writeString(folder.resolve(id + ".java"),
                    "public class " + id + " { public static int answer() { return 42; } }");
        }
        Path exam = Files.writeString(dir.resolve("exam.pdf"), "%PDF-1.4\n%%EOF\n");
        return new GenerationRequest(exam, resources, dir.resolve("output"), "");
    }

    private static class RecordingClient implements LlmClient {
        final GeneratedTesters reply = new GeneratedTesters();
        final UploadedDocument document = new UploadedDocument("file-exam", "file-exam");
        int calls, uploads, deletions;
        boolean fail;
        LlmRequest request;

        RecordingClient() { reply.tests = new ArrayList<>(); }
        public String providerName() { return "test"; }
        public boolean supportsStructuredOutput() { return true; }
        public String complete(LlmRequest request) { throw new AssertionError("use structured output"); }

        public <T> T completeStructured(LlmRequest request, Class<T> type) {
            calls++;
            this.request = request;
            if (fail) throw new LlmException("simulated request failure");
            return type.cast(reply);
        }

        public UploadedDocument uploadPdf(Path pdf) {
            uploads++;
            return document;
        }

        public void deleteUpload(UploadedDocument document) {
            assertEquals(this.document, document);
            deletions++;
        }
    }
}
