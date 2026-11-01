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
import generation.prompt.AddOnPrompter;
import generation.prompt.PromptBuilder;
import generation.prompt.PrompterRegistry;

public class TesterGenerator { //orchestrates pdf -> prompt -> llm -> validate -> write for every question
    private final PdfTextExtractor pdfExtractor = new PdfTextExtractor();
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
        // 1 > read the exam once > enumerate questions from the answer-template folders
        ExamDocument exam = pdfExtractor.extract(request.getExamPdf());
        List<QuestionSpec> specs = collector.collect(request.getResourcesDirectory(), exam);

        // 2 > resolve add-ons > explicit request list wins > otherwise fall back to config toggles
        List<AddOnPrompter> addOns = request.getEnabledAddOnPrompters().isEmpty()
                ? prompterRegistry.resolveFromConfig(config)
                : prompterRegistry.resolve(request.getEnabledAddOnPrompters());

        // 3 > optional: snapshot one existing tester as a format exemplar before we overwrite anything
        String exemplar = findExemplarTester(request.getOutputDirectory());

        // 4 > send the pdf natively only when configured AND the provider is gemini
        byte[] pdfBytes = (config.isLlmSendPdf() && "gemini".equals(config.getLlmProvider()))
                ? Files.readAllBytes(request.getExamPdf())
                : null;

        List<QuestionGenerationOutcome> outcomes = new ArrayList<>();
        for (QuestionSpec spec : specs) {
            outcomes.add(generateOne(spec, addOns, request.getUserPrompt(), exemplar, pdfBytes,
                    request.getOutputDirectory()));
        }
        return new GenerationResult(outcomes);
    }

    // generate & validate one tester > retry feeding the failure back into the prompt
    private QuestionGenerationOutcome generateOne(QuestionSpec spec, List<AddOnPrompter> addOns,
            String userPrompt, String exemplar, byte[] pdfBytes, Path outputDirectory) {
        int maxRetries = config.getGenerationMaxRetries();
        String lastFailure = "";

        // one initial attempt plus up to maxRetries more
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // on a retry, append the previous failure so the model can fix it
            String effectiveUserPrompt = buildRetryPrompt(userPrompt, lastFailure, attempt);

            String testerSource;
            try {
                String prompt = promptBuilder.build(spec, addOns, effectiveUserPrompt, exemplar);
                String reply = llmClient.complete(new LlmRequest(config.getLlmModel(), prompt, pdfBytes));
                testerSource = codeExtractor.extract(reply);
            } catch (RuntimeException error) {
                // llm or extraction failure > record & retry if attempts remain
                lastFailure = error.getMessage();
                if (attempt < maxRetries) {
                    continue;
                }
                return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.GENERATION_FAILED,
                        null, "generation failed > " + lastFailure);
            }

            // validate the generated tester against the real answer template
            // TODO needed for high quality tester, unnecessary for partial complate tester
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
                return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.VALIDATION_FAILED,
                        null, "failed validation after " + (maxRetries + 1) + " attempt(s) > " + lastFailure);
            }
        }

        // unreachable > the loop always returns > kept for the compiler
        return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.GENERATION_FAILED, null, "no attempt made");
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
