// Provides temporary submission, template, and ZIP fixtures for regression tests.
// Testing only; not required by the application.

package support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;

public final class SubmissionFixtures {
    public static final String HEADER = "// Name: Alice Tan\n// Email: alice@example.edu\n";

    private SubmissionFixtures() {}

    public static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    public static StudentDetails student() {
        return new StudentDetails("Alice Tan", "alice", "alice@example.edu");
    }

    public static StudentSubmission submission(Path root) {
        return new StudentSubmission(student(), root.resolve("alice.zip"), root);
    }

    public static QuestionDefinition question(Path resources, String id, String folder,
            List<String> required, List<String> allowed) {
        return new QuestionDefinition(id, folder, resources, required, allowed);
    }

    public static Path zip(Path directory, String name, Map<String, String> entries) throws IOException {
        Files.createDirectories(directory);
        Path archive = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }
}
