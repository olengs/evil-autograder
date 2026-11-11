package utils.files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import generation.QuestionSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import assessment.TesterMetadata;
import openai.GeneratedTesters;

public final class TesterWriter {
    private static final Gson json = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();

    private TesterWriter() {}

    public static void writeMarks(Map<String, Double> allocations, Path outputDirectory) throws IOException {
        StringBuilder marks = new StringBuilder();
        allocations.forEach((id, allocation) -> marks.append(id).append(':')
                .append(BigDecimal.valueOf(allocation).stripTrailingZeros().toPlainString()).append('\n'));
        writeFile(outputDirectory, "marks.txt", marks.toString());
    }

    public static void prepareOutput(List<QuestionSpec> specs, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        deletePrevious(specs, outputDirectory);
        writeFile(outputDirectory, "numTestCases.txt", "");
        for (QuestionSpec spec : specs) Files.createDirectories(outputDirectory.resolve(spec.folderName()));
    }

    // A failed generation must not leave old testers or a reply that looks current.
    public static void deletePrevious(List<QuestionSpec> specs, Path outputDirectory) throws IOException {
        Files.deleteIfExists(outputDirectory.resolve("response/all.json"));
        Files.deleteIfExists(outputDirectory.resolve("numTestCases.txt"));
        for (QuestionSpec spec : specs) {
            Files.deleteIfExists(outputDirectory.resolve(spec.testerFileName()));
            Files.deleteIfExists(outputDirectory.resolve(spec.folderName()).resolve(spec.testerFileName()));
        }
    }

    public static Path writeFilesBundle(String files, Path outputDirectory) throws IOException {
        return writeFile(outputDirectory.resolve("context"), "all.md", files);
    }

    public static Path writeReply(GeneratedTesters reply, Path outputDirectory) throws IOException {
        return writeFile(outputDirectory.resolve("response"), "all.json", json.toJson(reply));
    }

    public static Path writeForReview(QuestionSpec spec, String source, Path outputDirectory) throws IOException {
        return writeForReview(spec, source, List.of(), outputDirectory);
    }

    public static Path writeForReview(QuestionSpec spec, String source, List<GeneratedTesters.ResourceFile> resources,
            Path outputDirectory) throws IOException {
        Path review = Files.createTempDirectory(Files.createDirectories(outputDirectory.resolve("review")), "run-");
        Path file = write(spec, source, review);
        TesterResources.write(spec, resources, review);
        return file;
    }

    public static synchronized Path writeValidated(QuestionSpec spec, String source, int numTestCases, Path outputDirectory) throws IOException {
        return writeValidated(spec, source, numTestCases, List.of(), outputDirectory);
    }

    public static synchronized Path writeValidated(QuestionSpec spec, String source, int numTestCases,
            List<GeneratedTesters.ResourceFile> resources, Path outputDirectory) throws IOException {
        TesterResources.validate(spec, resources);
        if (numTestCases <= 0) throw new IOException("positive numTestCases required > " + spec.id());
        Path file = write(spec, source, outputDirectory.resolve(spec.folderName()));
        try {
            TesterResources.write(spec, resources, outputDirectory.resolve(spec.folderName()));
            TesterMetadata.readCaseCounts(outputDirectory); // reject malformed existing metadata
            Path metadataFile = outputDirectory.resolve("numTestCases.txt");
            var lines = new java.util.ArrayList<String>();
            if (Files.exists(metadataFile)) {
                for (String line : Files.readAllLines(metadataFile)) {
                    if (!line.split(":", 2)[0].strip().equalsIgnoreCase(spec.id())) lines.add(line);
                }
            }
            lines.add(spec.id() + ":" + numTestCases);
            String metadata = String.join("\n", lines) + "\n";
            writeFile(outputDirectory, "numTestCases.txt", metadata);
        } catch (IOException error) {
            Files.deleteIfExists(file);
            throw error;
        }
        return file;
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
