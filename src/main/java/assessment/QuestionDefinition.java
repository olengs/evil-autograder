package assessment;

import java.nio.file.Path;
import java.util.List;

/// stores a question's folder, answer filenames & support files
public class QuestionDefinition {
    private final String id;
    private final String expectedFolderName;
    private final Path resourceRoot;
    private final List<String> requiredAnswerFiles;
    private final List<String> supportFiles;

    public QuestionDefinition(String id, String expectedFolderName, Path resourceRoot, List<String> requiredAnswerFiles, List<String> supportFiles) {
        this.id = id;
        this.expectedFolderName = expectedFolderName;
        this.resourceRoot = resourceRoot;
        this.requiredAnswerFiles = List.copyOf(requiredAnswerFiles);
        this.supportFiles = List.copyOf(supportFiles);
    }

    public String getId() {
        return id;
    }

    public String getExpectedFolderName() {
        return expectedFolderName;
    }

    public Path getResourceDirectory() {
        return resourceRoot.resolve(expectedFolderName);
    }

    public List<String> getRequiredAnswerFiles() {
        return requiredAnswerFiles;
    }

    public List<String> getSupportFiles() {
        return supportFiles;
    }
}
