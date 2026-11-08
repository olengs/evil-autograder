package generation;

import config.Config;
import generation.QuestionGenerationOutcome.GenerationStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import openai.GeneratedTesters;
import openai.OpenAiClient;
import prompt.PromptBuilder;
import utils.files.PdfFileUtils;
import utils.files.TesterWriter;
import validation.GeneratedTesterValidator;
import validation.ValidationResult;

public class TesterGenerator {
    private final PromptBuilder promptBuilder = new PromptBuilder();

    private final Config config;
    private final OpenAiClient openAiClient;
    private final GeneratedTesterValidator validator;

    public TesterGenerator(Config config) {
        this.config = config;
        this.openAiClient = OpenAiClient.create(config);
        this.validator = new GeneratedTesterValidator(config.getCompilationTimeout(), config.getExecutionTimeout());
    }

    public GenerationResult generate(GenerationRequest request) throws IOException {
        // Discover questions in grading order.
        List<QuestionSpec> specs = QuestionSpec.collect(request.getResourcesDirectory());

        // Build and save the shared context; retire previous active testers.
        var bundle = promptBuilder.collectFiles(specs);
        Path outputDirectory = request.getOutputDirectory();
        TesterWriter.deletePrevious(specs, outputDirectory);
        TesterWriter.writeFilesBundle(bundle.text(), outputDirectory);

        // Attach the optional exam for one combined request.
        String documentId;
        try {
            documentId = config.isLlmSendPdf() ? openAiClient.uploadPdf(PdfFileUtils.requirePdf(request.getExamPdf())) : null;
        } catch (IOException | RuntimeException error) {
            return new GenerationResult(failedBatch(specs, "generation failed > " + error.getMessage()));
        }

        try {
            return new GenerationResult(generateTesters(specs, bundle, request, documentId));
        } finally {
            // Always clean up the uploaded exam, including after a failed request.
            if (documentId != null) openAiClient.deleteUpload(documentId);
        }
    }

    private List<QuestionGenerationOutcome> generateTesters(List<QuestionSpec> specs, PromptBuilder.FileBundle bundle,
            GenerationRequest request, String documentId) throws IOException {
        Path outputDirectory = request.getOutputDirectory();
        GeneratedTesters reply;
        try {
            String prompt = promptBuilder.build(specs, bundle.text(), request.getUserPrompt(), documentId != null);
            reply = openAiClient.generate(config.getLlmModel(), prompt, documentId);
        } catch (RuntimeException error) {
            return failedBatch(specs, "generation failed > " + error.getMessage());
        }

        // Save the reply before checking IDs so invalid responses can be reviewed.
        TesterWriter.writeReply(reply, outputDirectory);
        Map<String, GeneratedTesters.Tester> generated;
        try {
            generated = byQuestionId(reply, specs);
        } catch (IllegalArgumentException error) {
            return failedBatch(specs, "invalid response > " + error.getMessage());
        }

        List<QuestionGenerationOutcome> outcomes = new ArrayList<>();
        for (QuestionSpec spec : specs) {
            outcomes.add(validateAndSave(spec, generated.get(spec.getId()),
                    bundle.issuesByQuestion().get(spec.getId()), outputDirectory));
        }
        return outcomes;
    }

    private QuestionGenerationOutcome validateAndSave(QuestionSpec spec, GeneratedTesters.Tester entry,
            String issue, Path outputDirectory) {
        if (issue == null) issue = generationIssue(entry);
        if (issue != null) {
            return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.generationFailed, null, issue);
        }

        String source = entry.testCode.strip();
        ValidationResult validation = validator.validate(spec, source, entry.marks);
        GenerationStatus status = validation.isValid() ? GenerationStatus.written : GenerationStatus.validationFailed;
        String message = validation.isValid()
                ? "validated " + validation.testCaseCount() + " cases & final score"
                : validation.failedStage() + " > " + validation.message();
        try {
            Path file = validation.isValid() ? TesterWriter.write(spec, source, outputDirectory)
                    : TesterWriter.writeForReview(spec, source, outputDirectory);
            return new QuestionGenerationOutcome(spec.getId(), status, file, message);
        } catch (IOException error) {
            return new QuestionGenerationOutcome(spec.getId(), GenerationStatus.validationFailed, null,
                    message + " > could not write source > " + error.getMessage());
        }
    }

    private static String generationIssue(GeneratedTesters.Tester entry) {
        if (entry == null) return "no tester entry returned";
        if (entry.issue == null) return "missing issue field";
        if (!entry.issue.isBlank()) return entry.issue;
        if (entry.marks <= 0) return "positive marks were not established";
        if (entry.testCode == null || entry.testCode.isBlank()) return "no tester source returned";
        return null;
    }

    private static List<QuestionGenerationOutcome> failedBatch(List<QuestionSpec> specs, String message) {
        return specs.stream().map(spec -> new QuestionGenerationOutcome(
                spec.getId(), GenerationStatus.generationFailed, null, message)).toList();
    }

    private static Map<String, GeneratedTesters.Tester> byQuestionId(GeneratedTesters reply, List<QuestionSpec> specs) {
        if (reply == null || reply.tests == null) throw new IllegalArgumentException("missing tests array");
        var expected = specs.stream().map(QuestionSpec::getId).collect(Collectors.toSet());
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

}
