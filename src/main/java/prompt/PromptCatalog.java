package prompt;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.List;
import utils.files.TemplateUtils;

public final class PromptCatalog { //loads and checks the prompt JSON
    private static final String defaultResource = "/generation/prompt/tester-generation-prompt.json";

    //json shape > field names must match the keys in tester-generation-prompt.json
    public record Base(List<String> instructions) {}

    public record Section(String name, String requires, List<String> lines) {}

    //layout of the files.md bundle > one folder header then one entry per file
    public record Files(List<String> folder, List<String> textFile) {}

    private record Definition(Base base, Files files, List<Section> sections) {}

    private static PromptCatalog defaultCatalog; //lazily loaded once > shared by every builder

    private final Definition definition;

    private PromptCatalog(Definition definition) {
        this.definition = definition;
        validate();
    }

    public static synchronized PromptCatalog loadDefault() {
        if (defaultCatalog == null) {
            defaultCatalog = parse(TemplateUtils.readResource(defaultResource), defaultResource);
        }
        return defaultCatalog;
    }

    private static PromptCatalog parse(String json, String source) {
        try {
            Definition definition = new Gson().fromJson(json, Definition.class);
            if (definition == null) {
                throw new IllegalStateException("prompt catalog is empty > " + source);
            }
            return new PromptCatalog(definition);
        } catch (JsonParseException error) {
            throw new IllegalStateException("prompt catalog is not valid json > " + source + " > " + error.getMessage(),
                    error);
        }
    }

    // Reject missing sections before generation starts.
    private void validate() {
        if (definition.base() == null || isEmpty(definition.base().instructions())) {
            throw new IllegalStateException("prompt catalog needs base.instructions");
        }
        Files files = definition.files();
        if (files == null || isEmpty(files.folder()) || isEmpty(files.textFile())) {
            throw new IllegalStateException("prompt catalog needs files.folder & files.textFile");
        }
        if (isEmpty(definition.sections())) {
            throw new IllegalStateException("prompt catalog needs at least one section");
        }
        for (Section section : definition.sections()) {
            if (isEmpty(section.lines())) {
                throw new IllegalStateException("prompt catalog section has no lines > " + section.name());
            }
        }
    }

    public String getBaseInstructions() {
        return join(definition.base().instructions());
    }

    public Files getFiles() {
        return definition.files();
    }

    public List<Section> getSections() {
        return definition.sections();
    }

    //json stores multi-line text as an array of lines
    public static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
