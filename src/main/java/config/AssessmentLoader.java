package config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import model.AssessmentDefinition;
import model.QuestionDefinition;
import utils.FileUtils;

public class AssessmentLoader { //reads explicit layouts, or falls back to the original Q1/Q1a.java convention
    public static final String MANIFEST_NAME = "assessment.json";

    /** An optional assessment.json declares question-relative answers without filename assumptions. */
    public static AssessmentDefinition load(Path resources) throws IOException {
        Path root = resources.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("resource root is not a directory > " + root);
        }
        Path manifest = FileUtils.resolveContained(root, MANIFEST_NAME);
        try {
            if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) return loadManifest(root, manifest);
            return loadConventionalLayout(root);
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    private static AssessmentDefinition loadManifest(Path root, Path manifest) throws IOException {
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IOException("assessment.json must contain an object");
            JsonObject document = parsed.getAsJsonObject();
            String name = document.has("name") ? text(document.get("name"), "name") : root.getFileName().toString();
            JsonElement configured = document.get("questions");
            if (configured == null || !configured.isJsonArray() || configured.getAsJsonArray().isEmpty()) {
                throw new IOException("assessment.json requires a nonempty questions array");
            }
            List<QuestionDefinition> definitions = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Map<String, String> pathCase = new LinkedHashMap<>();
            Map<String, Set<String>> answersByFolder = new LinkedHashMap<>();
            for (JsonElement element : configured.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new IOException("each question must be an object");
                JsonObject question = element.getAsJsonObject();
                String id = text(question.get("id"), "question id");
                if (!ids.add(id.toLowerCase(Locale.ROOT))) throw new IOException("duplicate question id > " + id);
                String folder = relativePath(text(question.get("folder"), "question folder"));
                registerPathCase(folder, pathCase);
                Path directory = exactTemplatePath(root, folder, true);
                JsonElement required = question.get("requiredFiles");
                if (required == null || !required.isJsonArray() || required.getAsJsonArray().isEmpty()) {
                    throw new IOException("question " + id + " requires a nonempty requiredFiles array");
                }
                List<String> answers = readAnswers(required.getAsJsonArray(), directory, id, folder, pathCase);
                answersByFolder.computeIfAbsent(folder, ignored -> new LinkedHashSet<>()).addAll(answers);
                definitions.add(new QuestionDefinition(id, folder, root, answers, List.of()));
            }
            // Overlapping folders would copy another question's answers as trusted support.
            List<String> folders = new ArrayList<>(answersByFolder.keySet());
            for (int i = 0; i < folders.size(); i++) {
                for (int j = i + 1; j < folders.size(); j++) {
                    String first = folders.get(i).toLowerCase(Locale.ROOT);
                    String second = folders.get(j).toLowerCase(Locale.ROOT);
                    if (first.startsWith(second + "/") || second.startsWith(first + "/")) {
                        throw new IOException("question folders must not overlap > " + folders.get(i) + " and " + folders.get(j));
                    }
                }
            }
            List<QuestionDefinition> questions = new ArrayList<>();
            Map<String, List<String>> supportByFolder = new LinkedHashMap<>();
            for (var entry : answersByFolder.entrySet()) {
                supportByFolder.put(entry.getKey(), supportFiles(root.resolve(entry.getKey()), entry.getValue()));
            }
            for (QuestionDefinition question : definitions) {
                questions.add(new QuestionDefinition(question.getId(), question.getExpectedFolder(), root,
                        question.getRequiredFiles(), supportByFolder.get(question.getExpectedFolder())));
            }
            return new AssessmentDefinition(name, questions);
        } catch (JsonParseException | IllegalStateException error) {
            throw new IOException("invalid assessment.json > " + error.getMessage(), error);
        }
    }

    private static List<String> readAnswers(JsonArray configured, Path directory, String id,
            String folder, Map<String, String> pathCase) throws IOException {
        List<String> answers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : configured) {
            String answer = relativePath(text(element, "required file for " + id));
            if (!seen.add(answer.toLowerCase(Locale.ROOT))) throw new IOException("duplicate required file > " + answer);
            registerPathCase(folder + "/" + answer, pathCase);
            exactTemplatePath(directory, answer, false);
            answers.add(answer);
        }
        return List.copyOf(answers);
    }

    // Windows resolves wrong case successfully, so compare actual directory-entry names as strings.
    private static Path exactTemplatePath(Path root, String relative, boolean directory) throws IOException {
        FileUtils.resolveContained(root, relative);
        Path current = root;
        for (Path component : Path.of(relative)) {
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("template path is missing > " + relative);
            }
            String name = component.toString();
            try (var entries = Files.list(current)) {
                current = entries.filter(path -> path.getFileName().toString().equals(name)).findFirst()
                        .orElseThrow(() -> new IOException("template path is missing or uses different capitalization > " + relative));
            }
        }
        boolean valid = directory ? Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                : Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS);
        if (!valid) throw new IOException("invalid template " + (directory ? "folder" : "file") + " > " + relative);
        return current;
    }

    // One shared directory must have one spelling even when used by different questions or answers.
    private static void registerPathCase(String path, Map<String, String> declared) throws IOException {
        String prefix = "";
        for (String component : path.split("/")) {
            prefix = prefix.isEmpty() ? component : prefix + "/" + component;
            String previous = declared.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
            if (previous != null && !previous.equals(prefix)) {
                throw new IOException("inconsistent assessment path capitalization > " + previous + " and " + prefix);
            }
        }
    }

    private static String text(JsonElement value, String field) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw new IOException("missing or invalid " + field);
        return value.getAsString().strip();
    }

    private static String relativePath(String value) throws IOException {
        String portable = value.replace('\\', '/');
        try {
            Path path = Path.of(portable);
            if (path.isAbsolute() || portable.startsWith("/") || portable.matches("^[a-zA-Z]:.*")) {
                throw new IOException("assessment path must be relative > " + value);
            }
            for (String part : portable.split("/", -1)) {
                if (part.isBlank() || part.equals(".") || part.equals("..")) {
                    throw new IOException("invalid assessment path > " + value);
                }
            }
            return portable;
        } catch (InvalidPathException error) {
            throw new IOException("invalid assessment path > " + value, error);
        }
    }

    private static AssessmentDefinition loadConventionalLayout(Path root) throws IOException {
        List<QuestionDefinition> questions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<Path> folders;
        try (var entries = Files.list(root)) {
            folders = entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
        for (Path folder : folders) {
            String folderName = folder.getFileName().toString();
            if (folderName.startsWith(".")) continue;
            Pattern answerName = Pattern.compile(Pattern.quote(folderName) + "[a-zA-Z]*\\.java");
            List<String> answers = FileUtils.listFiles(folder).stream().map(path -> path.getFileName().toString())
                    .filter(name -> answerName.matcher(name).matches()).toList();
            if (answers.isEmpty()) continue;
            List<String> support = supportFiles(folder, new LinkedHashSet<>(answers));
            for (String answer : answers) {
                String id = answer.substring(0, answer.length() - 5);
                if (!ids.add(id.toLowerCase(Locale.ROOT))) throw new IOException("duplicate question id > " + id);
                questions.add(new QuestionDefinition(id, folderName, root, List.of(answer), support));
            }
        }
        if (questions.isEmpty()) {
            throw new IOException("no question templates found in " + root
                    + " > add assessment.json for custom layouts, or use Q1/Q1a.java");
        }
        return new AssessmentDefinition(root.getFileName().toString(), questions);
    }

    private static List<String> supportFiles(Path folder, Set<String> answers) throws IOException {
        List<String> support = new ArrayList<>();
        for (Path file : FileUtils.listFilesRecursively(folder)) {
            String name = folder.relativize(file).toString().replace('\\', '/');
            FileUtils.resolveContained(folder, name);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("invalid template file > " + file);
            boolean hidden = false;
            for (Path part : folder.relativize(file)) {
                if (part.toString().startsWith(".")) hidden = true;
            }
            if (!hidden && !answers.contains(name) && !isAnswerClass(name, answers)) support.add(name);
        }
        support.sort(String::compareTo);
        return List.copyOf(support);
    }

    private static boolean isAnswerClass(String file, Set<String> answers) {
        if (!file.endsWith(".class")) return false;
        String name = file.substring(0, file.length() - 6);
        int nested = name.indexOf('$', name.lastIndexOf('/') + 1);
        if (nested >= 0) name = name.substring(0, nested);
        return answers.contains(name + ".java");
    }
}
