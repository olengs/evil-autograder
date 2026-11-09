package prompt;

import utils.logging.JsonLineLogger;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import generation.QuestionSpec;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import utils.files.SupportContextCollector;
import utils.files.TemplateUtils;

public final class PromptBuilder {
    private static final String instructionsResource = "/generation/prompt/tester-generation-prompt.json";
    private static final String exampleResource = "/generation/prompt/tester-example.java.txt";
    private final String instructions = readInstructions();
    private final String testerExample = TemplateUtils.readResource(exampleResource).strip();

    private record Instructions(List<String> instructions) {}
    public record FileBundle(String text, Map<String, String> issuesByQuestion) {}

    public String build(List<QuestionSpec> specs, String files, String userPrompt) {
        if (specs == null || specs.isEmpty()) throw new IllegalArgumentException("at least one question spec is required");
        userPrompt = strip(userPrompt);
        List<String> sections = new ArrayList<>();
        sections.add(instructions);
        if (!userPrompt.isEmpty()) sections.add("ADDITIONAL INSTRUCTIONS FROM THE USER:\n" + userPrompt);
        String ids = specs.stream().map(QuestionSpec::id).collect(Collectors.joining(", "));
        sections.add("REQUESTED QUESTION IDS: " + ids
                + "\nReturn one entry per id, in this order. Keep all subparts separate in the response.");
        sections.add("BEGIN REFERENCE MATERIAL\n"
                + "Each folder contains its answer templates, declared support files, public dependency signatures and available class documentation. Context issues must be flagged for the affected questions.\n"
                + strip(files) + "\nEND REFERENCE MATERIAL");
        sections.add("QUESTION DESCRIPTION FROM THE EXAM:\n"
                + "The exam paper is attached as a PDF. Find the description of each question id in it (Question N, part (x)) and write tests that match its requirements and mark allocation.");
        sections.add("JAVA LAYOUT GUIDE (inside each successful testCode field):\n"
                + "Replace all markers. Repeat and adapt the example case until numTestCases cases are present, adapting setup and comparisons to the method. Use nested try/catch for expected exceptions.\n"
                + testerExample);
        return String.join("\n\n", sections) + "\n";
    }

    public FileBundle collectFiles(List<QuestionSpec> specs) throws InterruptedIOException {
        Map<String, List<QuestionSpec>> folders = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            folders.computeIfAbsent(spec.folderName(), name -> new ArrayList<>()).add(spec);
        }
        StringBuilder bundle = new StringBuilder();
        Map<String, String> issues = new LinkedHashMap<>();
        for (var folder : folders.entrySet()) {
            bundle.append(folder.getKey()).append("/\n");
            for (QuestionSpec spec : folder.getValue()) {
                appendFile(bundle, spec.id() + ".java", "java", spec.answerTemplateSource().strip());
            }
            var context = SupportContextCollector.collect(folder.getValue());
            for (var file : context.files()) appendFile(bundle, file.name(), file.type(), file.content());
            if (!context.issues().isEmpty()) {
                String issue = "Required context could not be read: " + String.join("; ", context.issues());
                bundle.append("CONTEXT ISSUE: ").append(issue).append("\n\n");
                for (QuestionSpec spec : folder.getValue()) issues.put(spec.id(), issue);
            }
        }
        return new FileBundle(bundle.toString(), Map.copyOf(issues));
    }

    private static void appendFile(StringBuilder bundle, String name, String type, String content) {
        bundle.append(name).append("\n```").append(type).append('\n').append(content).append("\n```\n\n");
    }

    private static String readInstructions() {
        try {
            var definition = new Gson().fromJson(TemplateUtils.readResource(instructionsResource), Instructions.class);
            if (definition == null || definition.instructions() == null || definition.instructions().isEmpty()) {
                throw new IllegalStateException("prompt needs instructions");
            }
            return String.join("\n", definition.instructions()).strip();
        } catch (JsonParseException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "PromptBuilder", error.toString());
            throw new IllegalStateException("prompt instructions are not valid json > " + error.getMessage(), error);
        }
    }

    private static String strip(String text) {
        return text == null ? "" : text.strip();
    }
}
