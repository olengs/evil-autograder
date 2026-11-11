package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import generation.QuestionSpec;
import openai.GeneratedTesters.ResourceFile;

/// Validates and writes the text data files returned with a generated tester.
public final class TesterResources {
    private TesterResources() {}

    public static void validate(QuestionSpec spec, List<ResourceFile> resources) throws IOException {
        if (resources == null) throw new IOException("missing resources array > " + spec.id());
        Set<String> names = new HashSet<>();
        for (var resource : resources) {
            if (resource == null || resource.fileName == null || resource.content == null) {
                throw new IOException("resource requires fileName and content > " + spec.id());
            }
            String name = resource.fileName;
            if (!name.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.(?i:txt|csv|json|xml|dat|properties|md)")) {
                throw new IOException("invalid text resource filename > " + name);
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("duplicate resource > " + name);
            if (spec.supportFiles().stream().anyMatch(file -> file.equalsIgnoreCase(name))) {
                throw new IOException("generated resource would replace supplied support > " + name);
            }
        }
    }

    public static void write(QuestionSpec spec, List<ResourceFile> resources, Path directory) throws IOException {
        validate(spec, resources);
        Files.createDirectories(directory);
        for (var resource : resources) {
            Path destination = directory.resolve(resource.fileName);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("resource destination must be a regular file > " + destination);
            }
            Files.writeString(destination, resource.content);
        }
    }
}
