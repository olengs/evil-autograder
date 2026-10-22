package model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class QuestionDefinition {
    private final String id;
    private final Path rootFolder;
    private final Path expectedFolder;
    private final List<Path> requiredFiles;
    private final List<Path> allowedResourceFiles;

    public QuestionDefinition(String id, String expectedFolder, String root, List<String> requiredFiles) { //eg. q1 > q1 > q1a.java &&& q1b.java
        this(id, expectedFolder,root, requiredFiles, List.of());
    }

    public QuestionDefinition(String id, String expectedFolder, String root, List<String> requiredFiles, List<String> allowedResourceFiles) {
        this.id = id;
        this.rootFolder = Path.of(root);
        this.expectedFolder = Path.of(expectedFolder);
        this.requiredFiles = requiredFiles.stream().filter(x -> x != null).map(x -> Path.of(x)).toList();
        this.allowedResourceFiles = allowedResourceFiles.stream().filter(x -> x != null).map(x -> Path.of(x)).toList();
    }

    public String getId() {
        return id;
    }

    public Path getRootFolderPath() {
        return rootFolder;
    }

    public Path getFolderPath() {
        return rootFolder.resolve(expectedFolder);
    }

    public Path getExpectedFolder() {
        return expectedFolder;
    }

    public List<Path> getRequiredFiles() {
        return Collections.unmodifiableList(requiredFiles); //read only
    }

    public List<Path> getAllowedResourceFiles() {
        return Collections.unmodifiableList(allowedResourceFiles);
    }
}
