package generation.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import generation.questionSpec.QuestionFile;
import generation.questionSpec.QuestionSpec;
import utils.TemplateUtils;

public final class PromptBuilder { // assembles one prompt by filling the section layout from tester-prompt.json
    private final PromptCatalog catalog;

    public PromptBuilder() {
        this(PromptCatalog.loadDefault());
    }

    public PromptBuilder(PromptCatalog catalog) {
        this.catalog = catalog;
    }

    // build one prompt asking for a tester per question in specs
    // files is the bundle from renderFiles > activeAddOns resolved by PrompterRegistry > userPrompt free-form >
    // exemplarTester a real tester to imitate > examAttached true when the exam pdf is uploaded with the request
    public String build(List<QuestionSpec> specs, String files, List<AddOnPrompter> activeAddOns,
            String userPrompt, String exemplarTester, boolean examAttached) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("at least one question spec is required");
        }

        // every value a section can reference > blank optional values make their section drop out
        Map<String, String> values = new HashMap<>();
        values.put("baseInstructions", catalog.getBaseInstructions().strip());
        values.put("addOnInstructions", activeAddOns == null ? ""
                : activeAddOns.stream().map(addOn -> addOn.instructions().strip())
                        .collect(Collectors.joining("\n\n")));
        values.put("userPrompt", strip(userPrompt));
        values.put("questionIds", specs.stream().map(QuestionSpec::getId).collect(Collectors.joining(", ")));
        values.put("files", strip(files));
        values.put("examAttached", examAttached ? "yes" : "");
        values.put("exemplarTester", strip(exemplarTester));

        // render sections in catalog order > skip a section whose required value is blank > blank line between
        return catalog.getSections().stream()
                .filter(section -> section.requires() == null || !values.getOrDefault(section.requires(), "").isBlank())
                .map(section -> TemplateUtils.fill(PromptCatalog.join(section.lines()), values))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }

    // the files.md bundle > every question folder once with all its files > layout from the catalog's files block
    // a spec without folder files contributes its answer template alone
    public String renderFiles(List<QuestionSpec> specs) {
        // questions sharing a folder (Q1a & Q1b) list the same files > keep each file once in folder order
        Map<String, List<QuestionFile>> folders = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            List<QuestionFile> folderFiles = folders.computeIfAbsent(folderName(spec), name -> new ArrayList<>());
            List<QuestionFile> files = spec.getFolderFiles().isEmpty()
                    ? List.of(new QuestionFile(spec.getId() + ".java", spec.getAnswerTemplateSource()))
                    : spec.getFolderFiles();
            for (QuestionFile file : files) {
                if (folderFiles.stream().noneMatch(existing -> existing.name().equals(file.name()))) {
                    folderFiles.add(file);
                }
            }
        }

        PromptCatalog.Files layout = catalog.getFiles();
        return folders.entrySet().stream()
                .map(folder -> TemplateUtils.fill(PromptCatalog.join(layout.folder()), Map.of("folderName", folder.getKey()))
                        + "\n" + folder.getValue().stream().map(file -> renderFile(layout, file))
                                .collect(Collectors.joining("\n\n")))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }

    // one file entry > binary files are named only
    private static String renderFile(PromptCatalog.Files layout, QuestionFile file) {
        if (file.isBinary()) {
            return TemplateUtils.fill(PromptCatalog.join(layout.binaryFile()), Map.of("fileName", file.name()));
        }
        return TemplateUtils.fill(PromptCatalog.join(layout.textFile()), Map.of(
                "fileName", file.name(),
                "fileType", extension(file.name()),
                "content", file.content().strip()));
    }

    // the question folder name > e.g. Q1 > the id when the spec has no folder
    private static String folderName(QuestionSpec spec) {
        return spec.getResourceDirectory() == null || spec.getResourceDirectory().getFileName() == null
                ? spec.getId()
                : spec.getResourceDirectory().getFileName().toString();
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
