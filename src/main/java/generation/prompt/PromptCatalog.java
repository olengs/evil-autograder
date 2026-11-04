package generation.prompt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import utils.TemplateUtils;

public final class PromptCatalog { //all prompt text loaded from tester-prompt.json > base, add-ons & section layout
    // bundled on the classpath from src/main/resources
    private static final String DEFAULT_RESOURCE = "/generation/prompt/tester-prompt.json";

    // json shape > field names must match the keys in tester-prompt.json
    public record Base(List<String> instructions) {}

    public record AddOn(String id, String description, List<String> instructions) {}

    public record Section(String name, String requires, List<String> lines) {}

    private record Definition(Base base, List<AddOn> addOns, List<Section> sections) {}

    private static PromptCatalog defaultCatalog; // lazily loaded once > shared by builder & registry

    private final Definition definition;

    private PromptCatalog(Definition definition) {
        this.definition = definition;
        validate();
    }

    // the bundled catalog > loaded on first use & cached
    public static synchronized PromptCatalog loadDefault() {
        if (defaultCatalog == null) {
            defaultCatalog = parse(TemplateUtils.readResource(DEFAULT_RESOURCE), DEFAULT_RESOURCE);
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

    // fail at load time rather than mid-generation > missing blocks or duplicate ids
    private void validate() {
        if (definition.base() == null || isEmpty(definition.base().instructions())) {
            throw new IllegalStateException("prompt catalog needs base.instructions");
        }
        if (isEmpty(definition.sections())) {
            throw new IllegalStateException("prompt catalog needs at least one section");
        }
        Set<String> ids = new HashSet<>();
        for (AddOn addOn : getAddOns()) {
            if (addOn.id() == null || addOn.id().isBlank() || !ids.add(addOn.id())) {
                throw new IllegalStateException("prompt catalog add-on id missing or duplicated > " + addOn.id());
            }
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

    public List<AddOn> getAddOns() {
        return definition.addOns() == null ? List.of() : definition.addOns();
    }

    public List<Section> getSections() {
        return definition.sections();
    }

    // json stores multi-line text as an array of lines
    public static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
