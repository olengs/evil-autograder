package prompt;

import generation.QuestionSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import utils.files.SupportContextCollector;
import utils.files.TemplateUtils;

public final class PromptBuilder {
    private static final String skeletonResource = "/generation/tester/tester-skeleton.java.txt";
    private static final String caseResource = "/generation/tester/tester-case.java.txt";

    private final PromptCatalog catalog = PromptCatalog.loadDefault();
    private final String testerSkeleton = TemplateUtils.readResource(skeletonResource).strip();
    private final String testerCase = TemplateUtils.readResource(caseResource).stripTrailing();

    public String build(List<QuestionSpec> specs, String files, String userPrompt, boolean examAttached) {
        if (specs == null || specs.isEmpty()) throw new IllegalArgumentException("at least one question spec is required");

        Map<String, String> values = new HashMap<>();
        values.put("baseInstructions", catalog.getBaseInstructions().strip());
        values.put("userPrompt", strip(userPrompt));
        values.put("questionIds", specs.stream().map(QuestionSpec::getId).collect(Collectors.joining(", ")));
        values.put("files", strip(files));
        values.put("examAttached", examAttached ? "yes" : "");
        // Template markers inside the Java examples must stay untouched.
        values.put("testerSkeleton", testerSkeleton);
        values.put("testerCase", testerCase);

        // Keep catalog order and omit sections whose required value is blank.
        return catalog.getSections().stream()
                .filter(section -> section.requires() == null || !values.getOrDefault(section.requires(), "").isBlank())
                .map(section -> TemplateUtils.fill(PromptCatalog.join(section.lines()), values))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }

    public record FileBundle(String text, Map<String, String> issuesByQuestion) {}

    public FileBundle collectFiles(List<QuestionSpec> specs) {
        //questions sharing a folder (Q1a & Q1b) are listed under one folder header in spec order
        Map<String, List<QuestionSpec>> folders = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            folders.computeIfAbsent(spec.getFolderName(), name -> new ArrayList<>()).add(spec);
        }

        String folderTemplate = PromptCatalog.join(catalog.getFiles().folder());
        String fileTemplate = PromptCatalog.join(catalog.getFiles().textFile());
        StringBuilder bundle = new StringBuilder();
        Map<String, String> issues = new LinkedHashMap<>();
        for (var folder : folders.entrySet()) {
            bundle.append(TemplateUtils.fill(folderTemplate, Map.of("folderName", folder.getKey())))
                    .append('\n');
            for (QuestionSpec spec : folder.getValue()) {
                bundle.append(renderFile(fileTemplate, spec.getId() + ".java", "java", spec.getAnswerTemplateSource().strip()))
                        .append("\n\n");
            }
            var context = SupportContextCollector.collect(folder.getValue());
            for (var file : context.files()) {
                bundle.append(renderFile(fileTemplate, file.name(), file.type(), file.content())).append("\n\n");
            }
            if (!context.issues().isEmpty()) {
                String issue = "Required context could not be read: " + String.join("; ", context.issues());
                bundle.append("CONTEXT ISSUE: ").append(issue).append("\n\n");
                for (QuestionSpec spec : folder.getValue()) issues.put(spec.getId(), issue);
            }
        }
        return new FileBundle(bundle.toString(), Map.copyOf(issues));
    }

    private static String renderFile(String template, String name, String type, String content) {
        return TemplateUtils.fill(template, Map.of("fileName", name, "fileType", type, "content", content));
    }

    private static String strip(String text) {
        return text == null ? "" : text.strip();
    }
}
