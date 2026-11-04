// Describes a question's expected folder, answers, and resources.
// Resolves its trusted template directory from the resource root.

package model;

import java.nio.file.Path;
import java.util.List;

public class QuestionDefinition { //describes what one question needs independently of any students submission
    private final String id;
    private final String expectedFolder; //@1ejun6 > eg. "q1" & "q1a.java"
    //@1ejun6 > store folder & file names as text > keep resource root as a path
    private final Path resourceRoot;
    private final List<String> requiredFiles; //@1ejun6 > eg. resources
    private final List<String> allowedResourceFiles; //@1ejun6 > eg. resources/q1/q1a.java

    public QuestionDefinition(String id, String expectedFolder, Path resourceRoot, List<String> requiredFiles, List<String> allowedResourceFiles) {
        this.id = id;
        this.expectedFolder = expectedFolder;
        this.resourceRoot = resourceRoot;
        this.requiredFiles = List.copyOf(requiredFiles);
        this.allowedResourceFiles = List.copyOf(allowedResourceFiles);
    }

    public String getId() {
        return id;
    }

    public String getExpectedFolder() {
        return expectedFolder;
    }

    public Path getResourceDirectory() { //@1ejun6 > build trusted resource folder
        return resourceRoot.resolve(expectedFolder);
    }

    public List<String> getRequiredFiles() {
        return requiredFiles;
    }

    public List<String> getAllowedResourceFiles() { //@1ejun6 > return support files this question is allowed to use
        return allowedResourceFiles;
    }
}
