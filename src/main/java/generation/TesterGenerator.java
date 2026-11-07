package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import config.Config;
import generation.llm.LlmClient;
import generation.llm.LlmClientFactory;
import generation.llm.LlmException;
import generation.llm.LlmRequest;
import generation.llm.UploadedDocument;
import generation.output.TesterWriter;
import generation.prompt.AddOnPrompter;
import generation.prompt.PromptBuilder;
import generation.prompt.PrompterRegistry;
import generation.questionSpec.QuestionSpec;
import generation.questionSpec.QuestionSpecCollector;
import generation.run.GenerationRequest;
import generation.run.GenerationResult;
import generation.run.GenerationStatus;
import generation.run.QuestionGenerationOutcome;
import generation.schema.GeneratedTesters;
import generation.validation.GeneratedTesterValidator;
import generation.validation.ValidationResult;
import utils.PdfFileUtils;

public class TesterGenerator { //exam pdf + all question templates -> one prompt -> llm json -> validate & write each tester
    private final QuestionSpecCollector collector = new QuestionSpecCollector();
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final TesterWriter writer = new TesterWriter();

    private final Config config;
    private final LlmClient llmClient;
    private final PrompterRegistry prompterRegistry;
    private final GeneratedTesterValidator validator;

    public TesterGenerator(Config config) {
        this(config, LlmClientFactory.create(config), new PrompterRegistry(),
                new GeneratedTesterValidator(config.getCompilationTimeout(), config.getExecutionTimeout()));
    }

    // explicit-dependency constructor > lets a ui or a test inject a stub llm client
    public TesterGenerator(Config config, LlmClient llmClient, PrompterRegistry prompterRegistry,
            GeneratedTesterValidator validator) {
        this.config = config;
        this.llmClient = llmClient;
        this.prompterRegistry = prompterRegistry;
        this.validator = validator;
    }

    // run generation for every discovered question > one bad question never aborts the rest
    public GenerationResult generate(GenerationRequest request) throws IOException {
        if (config.getGenerationMaxRetries() != 0) {
            throw new IllegalArgumentException("one-shot generation requires generation.max.retries=0");
        }
        // 1 > enumerate questions from the answer-template folders
        List<QuestionSpec> specs = collector.collect(request.getResourcesDirectory());

        // 2 > resolve add-ons > explicit request list wins > otherwise fall back to config toggles
        List<AddOnPrompter> addOns = request.getEnabledAddOnPrompters().isEmpty()
                ? prompterRegistry.resolveFromConfig(config)
                : prompterRegistry.resolve(request.getEnabledAddOnPrompters());

        // 3 > optional: one tester from exemplar.folder as a format example > none when the setting is blank
        String exemplar = config.getExemplarDirectory().map(this::findExemplarTester).orElse(null);

        if (!llmClient.supportsStructuredOutput()) {
            throw new LlmException(llmClient.providerName() + " cannot return structured output > "
                    + "generating testers as json needs it");
        }

        // 4 > bundle every question & subpart into one prompt > keep folder labels inside the bundle
        var bundle = promptBuilder.collectFiles(specs);
        writer.archivePrevious(specs, request.getOutputDirectory());
        writer.writeFilesBundle("all", bundle.text(), request.getOutputDirectory());

        // 5 > upload the exam pdf once when configured > an unsupported provider fails here before the llm call
        UploadedDocument exam = config.isLlmSendPdf()
                ? llmClient.uploadPdf(PdfFileUtils.requirePdf(request.getExamPdf()))
                : null;

        try {
            return new GenerationResult(generateTesters(specs, bundle, addOns,
                    request.getUserPrompt(), exemplar, exam, request.getOutputDirectory()));
        } finally {
            // always remove the uploaded copy > even when generation blew up
            if (exam != null) {
                llmClient.deleteUpload(exam);
            }
        }
    }

    private List<QuestionGenerationOutcome> generateTesters(List<QuestionSpec> specs, PromptBuilder.FileBundle bundle,
            List<AddOnPrompter> addOns, String userPrompt, String exemplar, UploadedDocument exam,
            Path outputDirectory) throws IOException {
        GeneratedTesters reply;
        try {
            String prompt = promptBuilder.build(specs, bundle.text(), addOns, userPrompt, exemplar, exam != null);
            reply = llmClient.completeStructured(new LlmRequest(config.getLlmModel(), prompt, exam), GeneratedTesters.class);
        } catch (RuntimeException error) {
            return failedBatch(specs, "generation failed > " + error.getMessage());
        }
        // Preserve the returned payload even when its ids or entries are invalid.
        writer.writeReply("all", reply, outputDirectory);
        Map<String, GeneratedTesters.Tester> generated;
        try {
            generated = byQuestionId(reply, specs);
        } catch (IllegalArgumentException error) {
            return failedBatch(specs, "invalid response > " + error.getMessage());
        }
        List<QuestionGenerationOutcome> outcomes = new ArrayList<>();
        for (QuestionSpec spec : specs) {
            var entry = generated.get(spec.getId());
            String issue = bundle.issuesByQuestion().get(spec.getId());
            if (issue == null && entry == null) issue = "no tester entry returned";
            if (issue == null && entry.issue == null) issue = "missing issue field";
            if (issue == null && !entry.issue.isBlank()) issue = entry.issue;
            if (issue == null && entry.marks <= 0) issue = "positive marks were not established";
            if (issue == null && (entry.testCode == null || entry.testCode.isBlank())) issue = "no tester source returned";
            if (issue != null) {
                outcomes.add(new QuestionGenerationOutcome(spec.getId(), GenerationStatus.GENERATION_FAILED, null, issue));
                continue;
            }
            String source = entry.testCode.strip();
            ValidationResult validation = validator.validate(spec, source, entry.marks);
            GenerationStatus status = validation.isValid() ? GenerationStatus.WRITTEN : GenerationStatus.VALIDATION_FAILED;
            String message = validation.isValid() ? "validated " + validation.getTestCaseCount() + " cases and final score"
                    : validation.getFailedStage() + " > " + validation.getMessage();
            try {
                Path file = validation.isValid() ? writer.write(spec, source, outputDirectory)
                        : writer.writeForReview(spec, source, outputDirectory);
                outcomes.add(new QuestionGenerationOutcome(spec.getId(), status, file, message));
            } catch (IOException error) {
                outcomes.add(new QuestionGenerationOutcome(spec.getId(), GenerationStatus.VALIDATION_FAILED, null,
                        message + " > could not write source > " + error.getMessage()));
            }
        }
        return outcomes;
    }

    private static List<QuestionGenerationOutcome> failedBatch(List<QuestionSpec> specs, String message) {
        return specs.stream().map(spec -> new QuestionGenerationOutcome(spec.getId(),
                GenerationStatus.GENERATION_FAILED, null, message)).toList();
    }

    private static Map<String, GeneratedTesters.Tester> byQuestionId(GeneratedTesters reply, List<QuestionSpec> specs) {
        if (reply == null || reply.tests == null) throw new IllegalArgumentException("missing tests array");
        var expected = specs.stream().map(QuestionSpec::getId).collect(java.util.stream.Collectors.toSet());
        Map<String, GeneratedTesters.Tester> entries = new LinkedHashMap<>();
        for (var entry : reply.tests) {
            if (entry == null || !expected.contains(entry.id)) {
                throw new IllegalArgumentException("unknown question id > " + (entry == null ? "null" : entry.id));
            }
            if (entries.putIfAbsent(entry.id, entry) != null) {
                throw new IllegalArgumentException("duplicate question id > " + entry.id);
            }
        }
        return entries;
    }

    // pick the lexicographically first existing *Tester.java as a format example > null when none
    private String findExemplarTester(Path exemplarDirectory) {
        if (!Files.isDirectory(exemplarDirectory)) {
            return null;
        }
        try (var files = Files.list(exemplarDirectory)) {
            Path exemplar = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Tester.java"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
            return exemplar == null ? null : Files.readString(exemplar);
        } catch (IOException error) {
            return null; // exemplar is optional > never fail generation over it
        }
    }
}
