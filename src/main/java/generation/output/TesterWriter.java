package generation.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import generation.questionSpec.QuestionSpec;

public class TesterWriter { //writes tester to the output folder > overwrites any existing one
    // Archive only the requested ids; a failed new run must not leave an old tester active.
    public void archivePrevious(List<QuestionSpec> specs, Path outputDirectory) throws IOException {
        Path archive = null;
        for (QuestionSpec spec : specs) {
            Path previous = outputDirectory.resolve(spec.getTesterFileName());
            if (!Files.exists(previous)) continue;
            if (archive == null) {
                Path archives = Files.createDirectories(outputDirectory.resolve("archive"));
                archive = Files.createTempDirectory(archives, "run-");
            }
            Files.move(previous, archive.resolve(spec.getTesterFileName()));
        }
    }

    public Path writeForReview(QuestionSpec spec, String source, Path outputDirectory) throws IOException {
        Path review = Files.createDirectories(outputDirectory.resolve("review"));
        Path run = Files.createTempDirectory(review, "run-");
        return write(spec, source, run);
    }
    // sub-folders of the output folder > keeps the testers folder clean for TesterLoader's *Tester.java glob
    private static final String CONTEXT_FOLDER = "context"; // the combined files bundle sent to the llm
    private static final String RESPONSE_FOLDER = "response"; // the llm's combined json reply
    // pretty-prints the model's structured reply > public fields like the schema classes
    private static final ObjectMapper JSON = new ObjectMapper();

    // write the files bundle to context/ so the supplied reference context can be inspected
    // replaced every run
    public Path writeFilesBundle(String bundleName, String files, Path outputDirectory) throws IOException {
        Path directory = Files.createDirectories(outputDirectory.toAbsolutePath().normalize().resolve(CONTEXT_FOLDER));
        return Files.writeString(directory.resolve(bundleName + ".md"), files);
    }

    // write the model's structured json reply to response/ > replaced every run
    public Path writeReply(String replyName, Object reply, Path outputDirectory) throws IOException {
        Path directory = Files.createDirectories(outputDirectory.toAbsolutePath().normalize().resolve(RESPONSE_FOLDER));
        return Files.writeString(directory.resolve(replyName + ".json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(reply));
    }

    // write the generated tester > return the path written > never touches answer templates
    public Path write(QuestionSpec spec, String testerSource, Path outputDirectory) throws IOException {
        if (testerSource == null || testerSource.isBlank()) {
            throw new IOException("refusing to write empty tester for " + spec.getId());
        }
        Path directory = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory); // create the output folder when missing

        Path target = directory.resolve(spec.getTesterFileName());
        Files.writeString(target, testerSource);
        return target;
    }
}
