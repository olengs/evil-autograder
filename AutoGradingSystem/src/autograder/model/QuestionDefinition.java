package autograder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionDefinition {

    private final String id;
    private final String expectedFolder;
    private final List<String> requiredFiles;

    public QuestionDefinition(
            String id,
            String expectedFolder,
            List<String> requiredFiles) {

        this.id = id;
        this.expectedFolder = expectedFolder;

        this.requiredFiles =
                new ArrayList<>(requiredFiles);
    }

    public String getId() {
        return id;
    }

    public String getExpectedFolder() {
        return expectedFolder;
    }

    public List<String> getRequiredFiles() {

        return Collections.unmodifiableList(
                requiredFiles);
    }
}