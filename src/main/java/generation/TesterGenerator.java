package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import config.Config;
import generation.llm.LlmClient;
import generation.llm.LlmClientFactory;
import generation.llm.LlmException;
import generation.llm.LlmRequest;
import generation.llm.UploadedDocument;
import generation.prompt.AddOnPrompter;
import generation.prompt.PromptBuilder;
import generation.prompt.PrompterRegistry;
import utils.PdfFileUtils;

public class TesterGenerator { //orchestrates files bundle & exam pdf upload -> one prompt -> llm json -> validate -> write per question
    private final QuestionSpecCollector collector = new QuestionSpecCollector();
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final TesterSourceRenderer renderer = new TesterSourceRenderer();
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
        // 1 > enumerate questions from the answer-template folders
        List<QuestionSpec> specs = collector.collect(request.getResourcesDirectory());

        // 2 > resolve add-ons > explicit request list wins > otherwise fall back to config toggles
        List<AddOnPrompter> addOns = request.getEnabledAddOnPrompters().isEmpty()
                ? prompterRegistry.resolveFromConfig(config)
                : prompterRegistry.resolve(request.getEnabledAddOnPrompters());

        // 3 > optional: snapshot one existing tester as a format exemplar before we overwrite anything
        String exemplar = findExemplarTester(request.getOutputDirectory());

        // 4 > every question folder as one files.md bundle > sent in the prompt & kept next to the testers for inspection
        String files = promptBuilder.renderFiles(specs);
        writer.writeFilesBundle(files, request.getOutputDirectory());

        // 5 > upload the exam pdf once when configured > an unsupported provider fails here before the llm call
        UploadedDocument exam = config.isLlmSendPdf()
                ? llmClient.uploadPdf(PdfFileUtils.requirePdf(request.getExamPdf()))
                : null;

        try {
            return new GenerationResult(generateAll(specs, files, addOns, request.getUserPrompt(), exemplar, exam,
                    request.getOutputDirectory()));
        } finally {
            // always remove the uploaded copy > even when generation blew up
            if (exam != null) {
                llmClient.deleteUpload(exam);
            }
        }
    }

    // one llm call returns every tester as json > each is rendered, validated & written on its own
    // a retry asks again for the failed question ids only, with each one's failure fed back into the prompt
    private List<QuestionGenerationOutcome> generateAll(List<QuestionSpec> specs, String files,
            List<AddOnPrompter> addOns, String userPrompt, String exemplar, UploadedDocument exam,
            Path outputDirectory) {
        if (!llmClient.supportsStructuredOutput()) {
            throw new LlmException(llmClient.providerName() + " cannot return structured output > "
                    + "generating every tester in one reply needs it");
        }
        int maxRetries = config.getGenerationMaxRetries();
        Map<String, QuestionGenerationOutcome> outcomes = new HashMap<>(); // question id -> final outcome
        Map<String, String> lastFailures = new LinkedHashMap<>(); // question id -> latest failure > fed into the retry
        Map<String, String> lastSources = new HashMap<>(); // keep each latest source so a failed one is still written
        List<QuestionSpec> pending = specs;

        // one initial attempt plus up to maxRetries more
        for (int attempt = 0; attempt <= maxRetries && !pending.isEmpty(); attempt++) {
            Map<String, GeneratedTesterSpec> generated;
            try {
                String prompt = promptBuilder.build(pending, files, addOns,
                        buildRetryPrompt(userPrompt, lastFailures), exemplar, exam != null);
                generated = byQuestionId(llmClient.completeStructured(
                        new LlmRequest(config.getLlmModel(), prompt, exam), GeneratedTesters.class));
            } catch (RuntimeException error) {
                // llm failure > every pending question shares it > retry if attempts remain
                pending.forEach(spec -> lastFailures.put(spec.getId(), "generation failed > " + error.getMessage()));
                continue;
            }

            List<QuestionSpec> failed = new ArrayList<>();
            for (QuestionSpec spec : pending) {
                GeneratedTesterSpec tester = generated.get(key(spec.getId()));
                String failure = renderAndValidate(spec, tester, lastSources);
                if (failure == null) {
                    outcomes.put(spec.getId(), writeTester(spec, lastSources.get(spec.getId()), outputDirectory,
                            GenerationStatus.WRITTEN, "validated with " + tester.testCases.size() + " test case(s)"));
                } else {
                    lastFailures.put(spec.getId(), failure);
                    failed.add(spec);
                }
            }
            // only the still-failing questions are asked for again > drop the failures of ones that now passed
            lastFailures.keySet().retainAll(failed.stream().map(QuestionSpec::getId).toList());
            pending = failed;
        }

        // out of attempts > write any best-effort (invalid) source for manual review, else report generation failure
        String attempts = (maxRetries + 1) + " attempt(s)";
        for (QuestionSpec spec : pending) {
            String failure = lastFailures.get(spec.getId());
            String source = lastSources.get(spec.getId());
            outcomes.put(spec.getId(), source != null
                    ? writeTester(spec, source, outputDirectory, GenerationStatus.VALIDATION_FAILED,
                            "failed after " + attempts + " > " + failure + " > written anyway for manual review")
                    : new QuestionGenerationOutcome(spec.getId(), GenerationStatus.GENERATION_FAILED, null,
                            "failed after " + attempts + " > " + failure));
        }
        return specs.stream().map(spec -> outcomes.get(spec.getId())).toList();
    }

    // render one tester from the model's json & validate it against the real answer template
    // returns null when valid > otherwise the failure to feed back > a rendered source is kept in lastSources either way
    private String renderAndValidate(QuestionSpec spec, GeneratedTesterSpec generated, Map<String, String> lastSources) {
        if (generated == null) {
            return "no tester was returned for " + spec.getId();
        }
        String testerSource;
        try {
            testerSource = renderer.render(spec, generated);
        } catch (IllegalArgumentException error) {
            return "unusable reply > " + error.getMessage();
        }
        lastSources.put(spec.getId(), testerSource);
        ValidationResult validation = validator.validate(spec, testerSource);
        return validation.isValid() ? null : validation.getFailedStage() + " > " + validation.getMessage();
    }

    // index the reply by question id > case-insensitive like the grader > the first entry wins on a duplicate
    private static Map<String, GeneratedTesterSpec> byQuestionId(GeneratedTesters reply) {
        Map<String, GeneratedTesterSpec> byId = new HashMap<>();
        if (reply != null && reply.testers != null) {
            for (GeneratedTesterSpec tester : reply.testers) {
                if (tester != null && tester.questionId != null) {
                    byId.putIfAbsent(key(tester.questionId), tester);
                }
            }
        }
        return byId;
    }

    private static String key(String questionId) {
        return questionId.strip().toLowerCase(Locale.ROOT);
    }

    // write the tester & report it with the given status > a write failure is reported instead, keeping the context
    private QuestionGenerationOutcome writeTester(QuestionSpec spec, String testerSource, Path outputDirectory,
            GenerationStatus status, String message) {
        try {
            Path written = writer.write(spec, testerSource, outputDirectory);
            return new QuestionGenerationOutcome(spec.getId(), status, written, message);
        } catch (IOException error) {
            return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.VALIDATION_FAILED, null,
                    message + " > but could not be written > " + error.getMessage());
        }
    }

    // append each failed question's error so the model can correct it on a retry > no failures on the first attempt
    private String buildRetryPrompt(String userPrompt, Map<String, String> failures) {
        if (failures.isEmpty()) {
            return userPrompt;
        }
        String retryNote = "The previous testers you produced for these question ids failed. "
                + "Fix them and return corrected testers:\n"
                + failures.entrySet().stream().map(failure -> "- " + failure.getKey() + " > " + failure.getValue())
                        .collect(Collectors.joining("\n"));
        return userPrompt.isBlank() ? retryNote : userPrompt + "\n\n" + retryNote;
    }

    // pick the lexicographically first existing *Tester.java as a format example > null when none
    private String findExemplarTester(Path outputDirectory) {
        if (!Files.isDirectory(outputDirectory)) {
            return null;
        }
        try (var files = Files.list(outputDirectory)) {
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
