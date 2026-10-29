package model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class QuestionDefinition {
    private final String id;
    private final Path rootFolder;
    private final Path expectedFolder;
    private final List<String> requiredFiles;
    private final List<Path> dependencyFiles;

    public QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles) {
        this(id, expectedFolder, requiredFiles, List.of());
    }

    public QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles, List<String> allowedResourceFiles) {
        this(id, expectedFolder, ".", requiredFiles, allowedResourceFiles.stream().map(Path::of).toList());
    }

    public QuestionDefinition(String id, String expectedFolder, String root, List<String> requiredFiles) { //eg. q1 > q1 > q1a.java &&& q1b.java
        this(id, expectedFolder,root, requiredFiles, List.of());
    }

    public QuestionDefinition(String id, String expectedFolder, String root, List<String> requiredFiles, List<Path> dependencyFiles) {
        this.id = id;
        this.rootFolder = Path.of(root);
        this.expectedFolder = Path.of(expectedFolder);
        this.requiredFiles = List.copyOf(requiredFiles);
        this.dependencyFiles = List.copyOf(dependencyFiles);
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

    public String getExpectedFolder() {
        return expectedFolder.toString();
    }

    public List<String> getAllowedResourceFiles() {
        return dependencyFiles.stream().map(Path::toString).toList();
    }

    public List<String> getRequiredFiles() {
        return Collections.unmodifiableList(requiredFiles); //read only
    }

    public List<Path> getDependencyFiles() {
        return Collections.unmodifiableList(dependencyFiles);
    }
}
