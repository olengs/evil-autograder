// Describes the expected files and templates for one question.
// Provides folder, required-file, and allowed-resource metadata.

package autograder.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionDefinition {
    private final String id;
    private final String expectedFolder;
    private final Path resourceRoot;
    private final List<String> requiredFiles;
    private final List<String> allowedResourceFiles;

    public QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles) { //eg. q1 > q1 > q1a.java &&& q1b.java
        this(id, expectedFolder, requiredFiles, List.of());
    }

    public QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles, List<String> allowedResourceFiles) {
        this(id, expectedFolder, null, requiredFiles, allowedResourceFiles);
    }

    public QuestionDefinition(String id, String expectedFolder, Path resourceRoot,
            List<String> requiredFiles, List<String> allowedResourceFiles) {
        this.id = id;
        this.expectedFolder = expectedFolder;
        this.resourceRoot = resourceRoot;
        this.requiredFiles = new ArrayList<>(requiredFiles);
        this.allowedResourceFiles = new ArrayList<>(allowedResourceFiles);
    }

    public String getId() {
        return id;
    }

    public String getExpectedFolder() {
        return expectedFolder;
    }

    /** Trusted template folder, or null for legacy definitions without a template root. */
    public Path getResourceDirectory() {
        return resourceRoot == null ? null : resourceRoot.resolve(expectedFolder);
    }

    public List<String> getRequiredFiles() {
        return Collections.unmodifiableList(requiredFiles); //read only
    }

    public List<String> getAllowedResourceFiles() {
        return Collections.unmodifiableList(allowedResourceFiles);
    }
}
