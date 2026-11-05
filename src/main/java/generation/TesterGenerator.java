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

public class TesterGenerator { //orchestrates exam pdf upload -> per folder: files bundle -> one prompt -> llm json -> validate -> write per question
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

        // 4 > group questions by folder > Q1a & Q1b share Q1 and its files > one llm call per folder
        Map<String, List<QuestionSpec>> folders = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            folders.computeIfAbsent(spec.getFolderName(), name -> new ArrayList<>()).add(spec);
        }

        // 5 > upload the exam pdf once when configured > an unsupported provider fails here before the llm call
        UploadedDocument exam = config.isLlmSendPdf()
                ? llmClient.uploadPdf(PdfFileUtils.requirePdf(request.getExamPdf()))
                : null;

        try {
            Map<String, QuestionGenerationOutcome> outcomes = new HashMap<>(); // question id -> final outcome
            for (Map.Entry<String, List<QuestionSpec>> folder : folders.entrySet()) {
                // each folder's own bundle e.g. context/q1.md > sent in its prompt & kept for inspection
                String bundleName = folder.getKey().toLowerCase(Locale.ROOT);
                String files = promptBuilder.renderFiles(folder.getValue());
                writer.writeFilesBundle(bundleName, files, request.getOutputDirectory());
                for (QuestionGenerationOutcome outcome : generateFolder(bundleName, folder.getValue(), files, addOns,
                        request.getUserPrompt(), exemplar, exam, request.getOutputDirectory())) {
                    outcomes.put(outcome.getQuestionId(), outcome);
                }
            }
            return new GenerationResult(specs.stream().map(spec -> outcomes.get(spec.getId())).toList());
        } finally {
            // always remove the uploaded copy > even when generation blew up
            if (exam != null) {
                llmClient.deleteUpload(exam);
            }
        }
    }

    // one llm call returns every tester in one question folder as json > each is validated & written on its own
    // a retry asks again for the failed question ids only, with each one's failure fed back into the prompt
    private List<QuestionGenerationOutcome> generateFolder(String bundleName, List<QuestionSpec> specs, String files,
            List<AddOnPrompter> addOns, String userPrompt, String exemplar, UploadedDocument exam,
            Path outputDirectory) {
        int maxRetries = config.getGenerationMaxRetries();
        Map<String, QuestionGenerationOutcome> outcomes = new HashMap<>(); // question id -> final outcome
        Map<String, String> lastFailures = new LinkedHashMap<>(); // question id -> latest failure > fed into the retry
        Map<String, String> lastSources = new HashMap<>(); // keep each latest source so a failed one is still written
        List<QuestionSpec> pending = specs;

        // one initial attempt plus up to maxRetries more
        for (int attempt = 0; attempt <= maxRetries && !pending.isEmpty(); attempt++) {
            GeneratedTesters reply;
            try {
                String prompt = promptBuilder.build(pending, files, addOns,
                        buildRetryPrompt(userPrompt, lastFailures), exemplar, exam != null);
                reply = llmClient.completeStructured(
                        new LlmRequest(config.getLlmModel(), prompt, exam), GeneratedTesters.class);
            } catch (RuntimeException error) {
                // llm failure > every pending question shares it > retry if attempts remain
                pending.forEach(spec -> lastFailures.put(spec.getId(), "generation failed > " + error.getMessage()));
                continue;
            }
            writeReply(attempt == 0 ? bundleName : bundleName + ".retry" + attempt, reply, outputDirectory);
            Map<String, GeneratedTesters.Tester> generated = byQuestionId(reply);

            List<QuestionSpec> failed = new ArrayList<>();
            for (QuestionSpec spec : pending) {
                ValidationResult validation = validate(spec, generated.get(key(spec.getId())), lastSources);
                if (validation != null && validation.isValid()) {
                    outcomes.put(spec.getId(), writeTester(spec, lastSources.get(spec.getId()), outputDirectory,
                            GenerationStatus.WRITTEN, "validated with " + validation.getTestCaseCount() + " test case(s)"));
                } else {
                    lastFailures.put(spec.getId(), validation == null
                            ? "no tester code was returned for " + spec.getId()
                            : validation.getFailedStage() + " > " + validation.getMessage());
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

    // validate the model's tester code against the real answer template
    // returns null when no code was returned > the source is kept in lastSources so a failed one is still written
    private ValidationResult validate(QuestionSpec spec, GeneratedTesters.Tester generated, Map<String, String> lastSources) {
        if (generated == null || generated.testCode == null || generated.testCode.isBlank()) {
            return null;
        }
        String testerSource = generated.testCode.strip();
        lastSources.put(spec.getId(), testerSource);
        return validator.validate(spec, testerSource);
    }

    // index tests by id > case-insensitive like the grader > the first entry wins on a duplicate
    private static Map<String, GeneratedTesters.Tester> byQuestionId(GeneratedTesters reply) {
        Map<String, GeneratedTesters.Tester> byId = new HashMap<>();
        if (reply != null && reply.tests != null) {
            for (GeneratedTesters.Tester tester : reply.tests) {
                if (tester != null && tester.id != null) {
                    byId.putIfAbsent(key(tester.id), tester);
                }
            }
        }
        return byId;
    }

    // "Q1a", "q1a" & "1a" all match question Q1a
    private static String key(String questionId) {
        String id = questionId.strip().toLowerCase(Locale.ROOT);
        return id.startsWith("q") ? id.substring(1) : id;
    }

    // keep the model's json reply in response/ for inspection > never fail generation over it
    private void writeReply(String replyName, GeneratedTesters reply, Path outputDirectory) {
        try {
            writer.writeReply(replyName, reply, outputDirectory);
        } catch (IOException error) {
            // inspection copy only
        }
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
