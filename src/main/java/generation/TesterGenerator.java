package generation;

import utils.logging.JsonLineLogger;
import config.Config;
import assessment.TesterMetadata;
import java.util.Locale;
import java.nio.file.Files;
import generation.QuestionGenerationOutcome.GenerationStatus;
import java.io.IOException;
import java.io.InterruptedIOException;
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
import utils.files.TesterResources;
import utils.process.Interruptions;
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

    public List<QuestionGenerationOutcome> generate(String userPrompt) throws IOException {
        return generate(userPrompt, GenerationListener.NONE);
    }

    public List<QuestionGenerationOutcome> generate(String userPrompt, GenerationListener listener) throws IOException {
        Interruptions.check("tester generation interrupted");
        listener.stageChanged("discovering questions");
        List<QuestionSpec> specs = QuestionSpec.collect(config.getResourcesDirectory());
        var allocations = TesterMetadata.readMarks(config.getTestersDirectory());
        for (QuestionSpec spec : specs) {
            Double marks = allocations.get(spec.id().toLowerCase(Locale.ROOT));
            if (marks == null || marks != Math.rint(marks) || marks > Integer.MAX_VALUE) {
                throw new IOException("generation requires positive integer marks.txt allocation for " + spec.id());
            }
        }
        listener.started(specs.stream().map(QuestionSpec::id).toList());

        listener.stageChanged("reading reference files");
        var bundle = promptBuilder.collectFiles(specs);
        Interruptions.check("tester generation interrupted");
        Path outputDirectory = config.getGenerationOutputDirectory();
        TesterWriter.prepareOutput(specs, allocations, outputDirectory);
        TesterWriter.writeFilesBundle(bundle.text(), outputDirectory);
        String allocationPrompt = "AUTHORITATIVE MARK ALLOCATION FROM marks.txt (overrides conflicting PDF marks):\n"
                + Files.readString(outputDirectory.resolve("marks.txt"));

        GeneratedTesters reply;
        try {
            reply = requestTesters(specs, bundle.text(), userPrompt + "\n" + allocationPrompt, listener);
        } catch (IOException | RuntimeException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGenerator", error.toString());
            Interruptions.check("tester generation interrupted");
            return failedBatch(specs, "generation failed > " + error.getMessage(), listener);
        }
        return processReply(specs, bundle, reply, outputDirectory, listener, allocations);
    }

    private GeneratedTesters requestTesters(List<QuestionSpec> specs, String files, String userPrompt,
            GenerationListener listener) throws IOException {
        String documentId = null;
        try {
            Interruptions.check("tester generation interrupted");
            if (config.isLlmSendPdf()) {
                listener.stageChanged("uploading exam PDF");
                documentId = openAiClient.uploadPdf(PdfFileUtils.requirePdf(config.getExamPdfFile()));
            }
            Interruptions.check("tester generation interrupted");
            String prompt = promptBuilder.build(specs, files, userPrompt, documentId != null);
            listener.stageChanged("waiting for OpenAI");
            Interruptions.check("tester generation interrupted");
            var reply = openAiClient.generate(config.getLlmModel(), prompt, documentId);
            Interruptions.check("tester generation interrupted");
            return reply;
        } finally {
            if (documentId != null) {
                // Give best-effort deletion a chance to run after cancellation.
                boolean interrupted = Thread.interrupted();
                try { openAiClient.deleteUpload(documentId); }
                finally { if (interrupted) Thread.currentThread().interrupt(); }
            }
        }
    }

    private List<QuestionGenerationOutcome> processReply(List<QuestionSpec> specs, PromptBuilder.FileBundle bundle,
            GeneratedTesters reply, Path outputDirectory, GenerationListener listener, Map<String, Double> allocations) throws IOException {
        Interruptions.check("tester generation interrupted");
        listener.stageChanged("checking returned testers");
        // Save the reply before checking IDs so invalid responses can be reviewed.
        TesterWriter.writeReply(reply, outputDirectory);
        Map<String, GeneratedTesters.Tester> generated;
        try {
            generated = byQuestionId(reply, specs);
        } catch (IllegalArgumentException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGenerator", error.toString());
            return failedBatch(specs, "invalid response > " + error.getMessage(), listener);
        }

        Map<String, String> resourceContents = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            var entry = generated.get(spec.id());
            if (entry == null || entry.resources == null) continue;
            for (var resource : entry.resources) {
                if (resource == null || resource.fileName == null || resource.content == null) continue;
                String key = (spec.folderName() + "/" + resource.fileName).toLowerCase(Locale.ROOT);
                String previous = resourceContents.putIfAbsent(key, resource.content);
                if (previous != null && !previous.equals(resource.content)) {
                    String message = "conflicting generated resource contents > " + key;
                    JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGenerator", message);
                    return failedBatch(specs, message, listener);
                }
            }
        }
        List<QuestionGenerationOutcome> outcomes = new ArrayList<>();
        for (QuestionSpec spec : specs) {
            Interruptions.check("tester generation interrupted");
            listener.questionStarted(spec.id());
            var outcome = validateAndSave(spec, generated.get(spec.id()),
                    bundle.issuesByQuestion().get(spec.id()), outputDirectory, allocations.get(spec.id().toLowerCase(Locale.ROOT)).intValue());
            outcomes.add(outcome);
            listener.questionFinished(outcome);
        }
        return List.copyOf(outcomes);
    }

    private QuestionGenerationOutcome validateAndSave(QuestionSpec spec, GeneratedTesters.Tester entry,
            String issue, Path outputDirectory, int allocatedMarks) throws InterruptedIOException {
        if (issue == null) issue = generationIssue(entry);
        if (issue == null && entry.marks != allocatedMarks) issue = "returned marks disagree with marks.txt > " + allocatedMarks;
        if (issue == null) {
            try {
                TesterResources.validate(spec, entry.resources);
            } catch (IOException error) {
                JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGenerator", error.toString());
                Interruptions.check("tester generation interrupted");
                issue = error.getMessage();
            }
        }
        if (issue != null) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGenerator", spec.id() + " > " + issue);
            return new QuestionGenerationOutcome(spec.id(), GenerationStatus.generationFailed, null, issue);
        }

        String source = entry.testCode.strip();
        ValidationResult validation = validator.validate(spec, source, entry.numTestCases, allocatedMarks, entry.resources);
        Interruptions.check("tester generation interrupted");
        GenerationStatus status = validation.isValid() ? GenerationStatus.written : GenerationStatus.validationFailed;
        String message = validation.isValid()
                ? "validated " + validation.testCaseCount() + " cases & final score"
                : validation.failedStage() + " > " + validation.message();
        try {
            Path file = validation.isValid() ? TesterWriter.writeValidated(spec, source, entry.numTestCases, entry.resources, outputDirectory)
                    : TesterWriter.writeForReview(spec, source, entry.resources, outputDirectory);
            return new QuestionGenerationOutcome(spec.id(), status, file, message);
        } catch (IOException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "TesterGenerator", error.toString());
            Interruptions.check("tester generation interrupted");
            return new QuestionGenerationOutcome(spec.id(), GenerationStatus.validationFailed, null,
                    message + " > could not write source > " + error.getMessage());
        }
    }

    private static String generationIssue(GeneratedTesters.Tester entry) {
        if (entry == null) return "no tester entry returned";
        if (entry.issue == null) return "missing issue field";
        if (!entry.issue.isBlank()) return entry.issue;
        if (entry.marks <= 0) return "positive marks were not established";
        if (entry.numTestCases <= 0) return "positive numTestCases was not established";
        if (entry.numTestCases % entry.marks != 0) return "numTestCases must be a multiple of marks";
        if (entry.testCode == null || entry.testCode.isBlank()) return "no tester source returned";
        return null;
    }

    private static List<QuestionGenerationOutcome> failedBatch(List<QuestionSpec> specs, String message, GenerationListener listener) {
        var outcomes = specs.stream().map(spec -> new QuestionGenerationOutcome(
                spec.id(), GenerationStatus.generationFailed, null, message)).toList();
        outcomes.forEach(listener::questionFinished);
        return outcomes;
    }

    private static Map<String, GeneratedTesters.Tester> byQuestionId(GeneratedTesters reply, List<QuestionSpec> specs) {
        if (reply == null || reply.tests == null) throw new IllegalArgumentException("missing tests array");
        var expected = specs.stream().map(QuestionSpec::id).collect(Collectors.toSet());
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
