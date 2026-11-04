package generation.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public final class PromptCatalog { //all prompt text loaded from tester-prompt.json > base, add-ons & section layout
    // bundled on the classpath from src/main/resources
    public static final String DEFAULT_RESOURCE = "/generation/prompt/tester-prompt.json";

    // {{name}} placeholder > filled in a single pass so inserted text is never re-scanned
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

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
            InputStream in = PromptCatalog.class.getResourceAsStream(DEFAULT_RESOURCE);
            if (in == null) {
                throw new IllegalStateException("prompt catalog not found on classpath > " + DEFAULT_RESOURCE);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                defaultCatalog = parse(reader, DEFAULT_RESOURCE);
            } catch (IOException error) {
                throw new UncheckedIOException("could not read prompt catalog > " + DEFAULT_RESOURCE, error);
            }
        }
        return defaultCatalog;
    }

    // an external catalog > lets a run swap prompts without rebuilding
    public static PromptCatalog load(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader, file.toString());
        }
    }

    private static PromptCatalog parse(Reader reader, String source) {
        try {
            Definition definition = new Gson().fromJson(reader, Definition.class);
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

    // replace every {{name}} with its value > an unknown name is a catalog typo so fail loudly
    public static String render(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        return matcher.replaceAll(match -> {
            String value = values.get(match.group(1));
            if (value == null) {
                throw new IllegalStateException("unknown prompt placeholder {{" + match.group(1) + "}}");
            }
            return Matcher.quoteReplacement(value);
        });
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
