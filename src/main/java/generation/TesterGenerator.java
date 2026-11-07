package generation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import config.Config;
import generation.llm.LlmClient;
import generation.llm.OpenAiClient;
import generation.llm.LlmRequest;
import generation.output.TesterWriter;
import generation.prompt.PromptBuilder;
import generation.questionSpec.QuestionSpec;
import generation.run.GenerationRequest;
import generation.run.GenerationResult;
import generation.run.QuestionGenerationOutcome.GenerationStatus;
import generation.run.QuestionGenerationOutcome;
import generation.schema.GeneratedTesters;
import generation.validation.GeneratedTesterValidator;
import generation.validation.ValidationResult;
import utils.files.PdfFileUtils;

public class TesterGenerator { //exam pdf & all question templates > one prompt > llm json > validate & write each tester
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final TesterWriter writer = new TesterWriter();

    private final Config config;
    private final LlmClient llmClient;
    private final GeneratedTesterValidator validator;

    public TesterGenerator(Config config) {
        this(config, OpenAiClient.create(config), new GeneratedTesterValidator(config.getCompilationTimeout(), config.getExecutionTimeout()));
    }

    //explicit dependency constructor > lets ui \ test inject a stub llm client
    public TesterGenerator(Config config, LlmClient llmClient, GeneratedTesterValidator validator) {
        this.config = config;
        this.llmClient = llmClient;
        this.validator = validator;
    }

    //generate every discovered question in one model request, then validate each returned tester
    public GenerationResult generate(GenerationRequest request) throws IOException {
        config.validateGenerationSettings();
        //1 > enumerate questions from answer template folders
        List<QuestionSpec> specs = QuestionSpec.collect(request.getResourcesDirectory());

        //2 > bundle every question & subpart into one prompt > keep folder labels inside bundle
        var bundle = promptBuilder.collectFiles(specs);
        writer.deletePrevious(specs, request.getOutputDirectory());
        writer.writeFilesBundle(bundle.text(), request.getOutputDirectory());

        //3 > upload optional exam pdf once for combined generation request
        String exam = config.isLlmSendPdf() ? llmClient.uploadPdf(PdfFileUtils.requirePdf(request.getExamPdf())) : null;

        try {
            return new GenerationResult(generateTesters(specs, bundle, request.getUserPrompt(), exam, request.getOutputDirectory()));
        } finally {
            //remove uploaded copy after single request and local validation, including on failure
            if (exam != null) llmClient.deleteUpload(exam);
        }
    }

    private List<QuestionGenerationOutcome> generateTesters(List<QuestionSpec> specs, PromptBuilder.FileBundle bundle, String userPrompt, String exam, Path outputDirectory) throws IOException {
        GeneratedTesters reply;
        try {
            String prompt = promptBuilder.build(specs, bundle.text(), userPrompt, exam != null);
            reply = llmClient.completeStructured(new LlmRequest(config.getLlmModel(), prompt, exam), GeneratedTesters.class);
        } catch (RuntimeException error) {
            return failedBatch(specs, "generation failed > " + error.getMessage());
        }

        //preserve returned payload even when its ids or entries are invalid.
        writer.writeReply(reply, outputDirectory);
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
                outcomes.add(new QuestionGenerationOutcome(spec.getId(), GenerationStatus.generationFailed, null, issue));
                continue;
            }

            String source = entry.testCode.strip();
            ValidationResult validation = validator.validate(spec, source, entry.marks);
            GenerationStatus status = validation.isValid() ? GenerationStatus.written : GenerationStatus.validationFailed;
            String message = validation.isValid() ? "validated " + validation.getTestCaseCount() + " cases & final score" : validation.getFailedStage() + " > " + validation.getMessage();

            try {
                Path file = validation.isValid() ? writer.write(spec, source, outputDirectory) : writer.writeForReview(spec, source, outputDirectory);
                outcomes.add(new QuestionGenerationOutcome(spec.getId(), status, file, message));
            } catch (IOException error) {
                outcomes.add(new QuestionGenerationOutcome(spec.getId(), GenerationStatus.validationFailed, null, message + " > could not write source > " + error.getMessage()));
            }
        }
        return outcomes;
    }

    private static List<QuestionGenerationOutcome> failedBatch(List<QuestionSpec> specs, String message) {
        return specs.stream().map(spec -> new QuestionGenerationOutcome(spec.getId(), GenerationStatus.generationFailed, null, message)).toList();
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

}
