package generation;

import java.nio.file.Path;
import java.util.List;

public final class QuestionSpec { //everything the llm needs to write one tester > id & template signature
    private final String id; // question id & answer class name > e.g. Q1a or Q3
    private final String answerTemplateSource; // full source of the template > gives the exact method signature
    private final Path resourceDirectory; // the question folder holding support files > e.g. .../Q2
    private final List<String> supportFiles; // support files needed to compile & run > e.g. DataException.class
    private final List<QuestionFile> folderFiles; // every file in the question folder > sent to the llm as context

    public QuestionSpec(String id, String answerTemplateSource, Path resourceDirectory, List<String> supportFiles) {
        this(id, answerTemplateSource, resourceDirectory, supportFiles, List.of());
    }

    public QuestionSpec(String id, String answerTemplateSource, Path resourceDirectory, List<String> supportFiles,
            List<QuestionFile> folderFiles) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("question id is required");
        }
        if (answerTemplateSource == null || answerTemplateSource.isBlank()) {
            throw new IllegalArgumentException("answerTemplateSource is required for " + id);
        }
        this.id = id;
        this.answerTemplateSource = answerTemplateSource;
        this.resourceDirectory = resourceDirectory;
        // copy defensively > empty when the question has no support files
        this.supportFiles = supportFiles == null ? List.of() : List.copyOf(supportFiles);
        this.folderFiles = folderFiles == null ? List.of() : List.copyOf(folderFiles);
    }

    public String getId() {
        return id;
    }

    public String getAnswerTemplateSource() {
        return answerTemplateSource;
    }

    public Path getResourceDirectory() {
        return resourceDirectory;
    }

    public List<String> getSupportFiles() {
        return supportFiles;
    }

    public List<QuestionFile> getFolderFiles() {
        return folderFiles;
    }

    // name of the tester file this question should produce > matches TesterLoader discovery glob
    public String getTesterFileName() {
        return id + "Tester.java";
    }

    // main class the grader runs > filename without .java
    public String getTesterMainClass() {
        return id + "Tester";
    }

    @Override
    public String toString() {
        return "QuestionSpec{id=" + id + ", resourceDirectory=" + resourceDirectory
                + ", templateSourceLength=" + answerTemplateSource.length() + '}';
    }
}
