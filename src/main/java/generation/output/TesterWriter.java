package generation.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import generation.questionSpec.QuestionSpec;

public class TesterWriter { //writes tester to the output folder > overwrites any existing one
    // sub-folders of the output folder > keeps the testers folder clean for TesterLoader's *Tester.java glob
    private static final String CONTEXT_FOLDER = "context"; // the files bundle sent to the llm per question folder
    private static final String RESPONSE_FOLDER = "response"; // the llm's json reply per question folder
    // pretty-prints the model's structured reply > public fields like the schema classes
    private static final ObjectMapper JSON = new ObjectMapper();

    // write one folder's files bundle to context/ e.g. context/q1.md so the exact llm context can be inspected
    // replaced every run
    public Path writeFilesBundle(String bundleName, String files, Path outputDirectory) throws IOException {
        Path directory = Files.createDirectories(outputDirectory.toAbsolutePath().normalize().resolve(CONTEXT_FOLDER));
        return Files.writeString(directory.resolve(bundleName + ".md"), files);
    }

    // write the model's json reply to response/ e.g. response/q1.json > replaced every run
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
