package generation.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import generation.questionSpec.QuestionSpec;
import utils.TemplateUtils;

public final class PromptBuilder { // assembles one prompt by filling the section layout from tester-prompt.json
    // the tester layout the model must follow > filled into the tester-template section as-is
    private static final String SKELETON_RESOURCE = "/generation/tester/tester-skeleton.java.txt";
    private static final String CASE_RESOURCE = "/generation/tester/tester-case.java.txt";

    private final PromptCatalog catalog;
    private final String testerSkeleton = TemplateUtils.readResource(SKELETON_RESOURCE).strip();
    private final String testerCase = TemplateUtils.readResource(CASE_RESOURCE).stripTrailing();

    public PromptBuilder() {
        this(PromptCatalog.loadDefault());
    }

    public PromptBuilder(PromptCatalog catalog) {
        this.catalog = catalog;
    }

    // build one prompt asking for a tester per question in specs
    // files is the bundle from renderFiles > userPrompt free-form >
    // exemplarTester a real tester to imitate > examAttached true when the exam pdf is uploaded with the request
    public String build(List<QuestionSpec> specs, String files, String userPrompt, String exemplarTester, boolean examAttached) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("at least one question spec is required");
        }

        // every value a section can reference > blank optional values make their section drop out
        Map<String, String> values = new HashMap<>();
        values.put("baseInstructions", catalog.getBaseInstructions().strip());
        values.put("userPrompt", strip(userPrompt));
        values.put("questionIds", specs.stream().map(QuestionSpec::getId).collect(Collectors.joining(", ")));
        values.put("files", strip(files));
        values.put("examAttached", examAttached ? "yes" : "");
        values.put("exemplarTester", strip(exemplarTester));
        // filled in one pass > the {{...}} markers inside the templates are not replaced
        values.put("testerSkeleton", testerSkeleton);
        values.put("testerCase", testerCase);

        // render sections in catalog order > skip a section whose required value is blank > blank line between
        return catalog.getSections().stream()
                .filter(section -> section.requires() == null || !values.getOrDefault(section.requires(), "").isBlank())
                .map(section -> TemplateUtils.fill(PromptCatalog.join(section.lines()), values))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }

    // the <folder>.md bundle > each question folder in specs once with its answer templates > layout from the catalog's files block
    public String renderFiles(List<QuestionSpec> specs) {
        return collectFiles(specs).text();
    }

    public record FileBundle(String text, Map<String, String> issuesByQuestion) {}

    public FileBundle collectFiles(List<QuestionSpec> specs) {
        // questions sharing a folder (Q1a & Q1b) are listed under one folder header in spec order
        Map<String, List<QuestionSpec>> folders = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            folders.computeIfAbsent(spec.getFolderName(), name -> new ArrayList<>()).add(spec);
        }

        PromptCatalog.Files layout = catalog.getFiles();
        StringBuilder bundle = new StringBuilder();
        Map<String, String> issues = new LinkedHashMap<>();
        for (var folder : folders.entrySet()) {
            bundle.append(TemplateUtils.fill(PromptCatalog.join(layout.folder()), Map.of("folderName", folder.getKey())))
                    .append('\n');
            for (QuestionSpec spec : folder.getValue()) bundle.append(renderTemplate(layout, spec)).append("\n\n");
            var context = new SupportContextCollector().collect(folder.getValue());
            for (var file : context.files()) {
                bundle.append(TemplateUtils.fill(PromptCatalog.join(layout.textFile()), Map.of(
                        "fileName", file.name(), "fileType", file.type(), "content", file.content()))).append("\n\n");
            }
            if (!context.issues().isEmpty()) {
                String issue = "Required context could not be read: " + String.join("; ", context.issues());
                bundle.append("CONTEXT ISSUE: ").append(issue).append("\n\n");
                for (QuestionSpec spec : folder.getValue()) issues.put(spec.getId(), issue);
            }
        }
        return new FileBundle(bundle.toString(), Map.copyOf(issues));
    }

    // one answer template entry e.g. Q1a.java
    private static String renderTemplate(PromptCatalog.Files layout, QuestionSpec spec) {
        String fileName = spec.getId() + ".java";
        return TemplateUtils.fill(PromptCatalog.join(layout.textFile()), Map.of(
                "fileName", fileName,
                "fileType", extension(fileName),
                "content", spec.getAnswerTemplateSource().strip()));
    }

    // file extension used as the fence language > e.g. java or txt > empty when there is none
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String strip(String text) {
        return text == null ? "" : text.strip();
    }
}
