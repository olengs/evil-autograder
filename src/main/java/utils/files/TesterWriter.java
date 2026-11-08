package utils.files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import generation.QuestionSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import openai.GeneratedTesters;

public final class TesterWriter {
    private static final Gson json = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();

    private TesterWriter() {}

    // A failed generation must not leave old testers or a reply that looks current.
    public static void deletePrevious(List<QuestionSpec> specs, Path outputDirectory) throws IOException {
        Files.deleteIfExists(outputDirectory.resolve("response/all.json"));
        for (QuestionSpec spec : specs) Files.deleteIfExists(outputDirectory.resolve(spec.testerFileName()));
    }

    public static Path writeFilesBundle(String files, Path outputDirectory) throws IOException {
        return writeFile(outputDirectory.resolve("context"), "all.md", files);
    }

    public static Path writeReply(GeneratedTesters reply, Path outputDirectory) throws IOException {
        return writeFile(outputDirectory.resolve("response"), "all.json", json.toJson(reply));
    }

    public static Path writeForReview(QuestionSpec spec, String source, Path outputDirectory) throws IOException {
        Path review = Files.createDirectories(outputDirectory.resolve("review"));
        return write(spec, source, Files.createTempDirectory(review, "run-"));
    }

    public static Path write(QuestionSpec spec, String source, Path outputDirectory) throws IOException {
        if (source == null || source.isBlank()) throw new IOException("refusing to write empty tester for " + spec.id());
        return writeFile(outputDirectory, spec.testerFileName(), source);
    }

    private static Path writeFile(Path directory, String name, String text) throws IOException {
        Path target = Files.createDirectories(directory.toAbsolutePath().normalize()).resolve(name);
        return Files.writeString(target, text);
    }
}
