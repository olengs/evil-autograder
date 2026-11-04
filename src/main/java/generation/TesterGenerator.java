package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import config.Config;
import generation.llm.LlmClient;
import generation.llm.LlmClientFactory;
import generation.llm.LlmRequest;
import generation.llm.UploadedDocument;
import generation.prompt.AddOnPrompter;
import generation.prompt.PromptBuilder;
import generation.prompt.PrompterRegistry;
import utils.PdfFileUtils;

public class TesterGenerator { //orchestrates exam pdf upload -> prompt -> llm -> validate -> write for every question
    private final QuestionSpecCollector collector = new QuestionSpecCollector();
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final TesterCodeExtractor codeExtractor = new TesterCodeExtractor();
    private final TesterWriter writer = new TesterWriter();

    private final Config config;
    private final LlmClient llmClient;
    private final PrompterRegistry prompterRegistry;
    private final GeneratedTesterValidator validator;

    public TesterGenerator(Config config) {
        this(config, LlmClientFactory.create(config), new PrompterRegistry(config),
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

        // 4 > upload the exam pdf once when configured > an unsupported provider fails here before any question runs
        UploadedDocument exam = config.isLlmSendPdf()
                ? llmClient.uploadPdf(PdfFileUtils.requirePdf(request.getExamPdf()))
                : null;

        List<QuestionGenerationOutcome> outcomes = new ArrayList<>();
        try {
            for (QuestionSpec spec : specs) {
                outcomes.add(generateOne(spec, addOns, request.getUserPrompt(), exemplar, exam,
                        request.getOutputDirectory()));
            }
        } finally {
            // always remove the uploaded copy > even when a question blew up
            llmClient.deleteUpload(exam);
        }
        return new GenerationResult(outcomes);
    }

    // generate & validate one tester > retry feeding the failure back into the prompt
    private QuestionGenerationOutcome generateOne(QuestionSpec spec, List<AddOnPrompter> addOns,
            String userPrompt, String exemplar, UploadedDocument exam, Path outputDirectory) {
        int maxRetries = config.getGenerationMaxRetries();
        String lastFailure = "";
        String lastTesterSource = null; // keep the most recent generated source so a failed one is still written

        // one initial attempt plus up to maxRetries more
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // on a retry, append the previous failure so the model can fix it
            String effectiveUserPrompt = buildRetryPrompt(userPrompt, lastFailure, attempt);

            String testerSource;
            try {
                String prompt = promptBuilder.build(spec, addOns, effectiveUserPrompt, exemplar, exam != null);
                String reply = llmClient.complete(new LlmRequest(config.getLlmModel(), prompt, exam));
                testerSource = codeExtractor.extract(reply);
            } catch (RuntimeException error) {
                // llm or extraction failure > record & retry if attempts remain
                lastFailure = error.getMessage();
                if (attempt < maxRetries) {
                    continue;
                }
                // nothing usable was ever produced > fall back to any earlier source, else report generation failure
                if (lastTesterSource != null) {
                    return writeFlagged(spec, lastTesterSource, outputDirectory,
                            "generation failed on final attempt > " + lastFailure);
                }
                return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.GENERATION_FAILED,
                        null, "generation failed > " + lastFailure);
            }

            lastTesterSource = testerSource; // remember the latest source even if it fails validation

            // validate the generated tester against the real answer template
            ValidationResult validation = validator.validate(spec, testerSource);
            if (validation.isValid()) {
                try {
                    Path written = writer.write(spec, testerSource, outputDirectory);
                    return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.WRITTEN,
                            written, "validated with " + validation.getTestCaseCount() + " test case(s)");
                } catch (IOException error) {
                    return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.VALIDATION_FAILED,
                            null, "validated but could not be written > " + error.getMessage());
                }
            }

            // invalid > keep the failure detail for the next retry prompt
            lastFailure = validation.getFailedStage() + " > " + validation.getMessage();
            if (attempt >= maxRetries) {
                // still write the best-effort (invalid) tester so it can be inspected & fixed by hand > but flag it
                return writeFlagged(spec, testerSource, outputDirectory,
                        "failed validation after " + (maxRetries + 1) + " attempt(s) > " + lastFailure);
            }
        }

        // unreachable > the loop always returns > kept for the compiler
        return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.GENERATION_FAILED, null, "no attempt made");
    }

    // write a tester that did not pass validation so it is not thrown away > status stays VALIDATION_FAILED
    private QuestionGenerationOutcome writeFlagged(QuestionSpec spec, String testerSource,
            Path outputDirectory, String reason) {
        try {
            Path written = writer.write(spec, testerSource, outputDirectory);
            return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.VALIDATION_FAILED,
                    written, reason + " > written anyway for manual review");
        } catch (IOException error) {
            // writing the flagged tester itself failed > report that, keeping the validation failure context
            return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.VALIDATION_FAILED,
                    null, reason + " > and could not be written > " + error.getMessage());
        }
    }

    // append the previous failure so the model can correct it on a retry
    private String buildRetryPrompt(String userPrompt, String lastFailure, int attempt) {
        if (attempt == 0 || lastFailure == null || lastFailure.isBlank()) {
            return userPrompt;
        }
        String retryNote = "\nThe previous tester you produced failed with this error. Fix it and return corrected code:\n"
                + lastFailure;
        return (userPrompt == null || userPrompt.isBlank()) ? retryNote : userPrompt + "\n" + retryNote;
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
