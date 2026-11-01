package autograder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class QuestionDefinition {
    private final String id;
    private final String expectedFolder;
    private final List<String> requiredFiles;
    private final List<String> allowedResourceFiles;
    private final List<String> editableFiles;

    public QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles) { //eg. q1 > q1 > q1a.java &&& q1b.java
        this(id, expectedFolder, requiredFiles, List.of());
    }

    public QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles, List<String> allowedResourceFiles) {
        this(id, expectedFolder, requiredFiles, allowedResourceFiles, requiredFiles);
    }

    private QuestionDefinition(String id, String expectedFolder, List<String> requiredFiles,
            List<String> allowedResourceFiles, List<String> editableFiles) {
        this.id = id;
        this.expectedFolder = expectedFolder;
        this.requiredFiles = new ArrayList<>(requiredFiles);
        this.allowedResourceFiles = new ArrayList<>(allowedResourceFiles);
        LinkedHashSet<String> selected = new LinkedHashSet<>(requiredFiles);
        selected.addAll(editableFiles);
        this.editableFiles = List.copyOf(selected);
    }

    public String getId() {
        return id;
    }

    public String getExpectedFolder() {
        return expectedFolder;
    }

    public List<String> getRequiredFiles() {
        return Collections.unmodifiableList(requiredFiles); //read only
    }

    public List<String> getAllowedResourceFiles() {
        return Collections.unmodifiableList(allowedResourceFiles);
    }

    public List<String> getEditableFiles() {
        return editableFiles;
    }

    public QuestionDefinition withEditableFiles(List<String> files) {
        return new QuestionDefinition(id, expectedFolder, requiredFiles, allowedResourceFiles, files);
    }
}
