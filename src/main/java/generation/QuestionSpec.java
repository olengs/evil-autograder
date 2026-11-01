package generation;

import java.nio.file.Path;
import java.util.List;

public final class QuestionSpec { //everything the llm needs to write one tester > id & template signature & pdf excerpt
    private final String id; // question id & answer class name > e.g. Q1a or Q3
    private final String folder; // answer-template folder > e.g. Q1 or Q3
    private final Path answerTemplateFile; // path to the student answer template > e.g. Q1/Q1a.java
    private final String answerTemplateSource; // full source of the template > gives the exact method signature
    private final String pdfExcerpt; // slice of the exam text describing this question
    private final Path resourceDirectory; // the question folder holding support files > e.g. .../Q2
    private final List<String> supportFiles; // support files needed to compile & run > e.g. DataException.class

    public QuestionSpec(String id, String folder, Path answerTemplateFile,
            String answerTemplateSource, String pdfExcerpt,
            Path resourceDirectory, List<String> supportFiles) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("question id is required");
        }
        if (answerTemplateFile == null) {
            throw new IllegalArgumentException("answerTemplateFile is required for " + id);
        }
        if (answerTemplateSource == null || answerTemplateSource.isBlank()) {
            throw new IllegalArgumentException("answerTemplateSource is required for " + id);
        }
        this.id = id;
        this.folder = folder == null ? "" : folder;
        this.answerTemplateFile = answerTemplateFile;
        this.answerTemplateSource = answerTemplateSource;
        // excerpt may be blank when the pdf heading could not be matched > caller decides what to do
        this.pdfExcerpt = pdfExcerpt == null ? "" : pdfExcerpt;
        this.resourceDirectory = resourceDirectory;
        // copy defensively > empty when the question has no support files
        this.supportFiles = supportFiles == null ? List.of() : List.copyOf(supportFiles);
    }

    public String getId() {
        return id;
    }

    public String getFolder() {
        return folder;
    }

    public Path getAnswerTemplateFile() {
        return answerTemplateFile;
    }

    public String getAnswerTemplateSource() {
        return answerTemplateSource;
    }

    public String getPdfExcerpt() {
        return pdfExcerpt;
    }

    public Path getResourceDirectory() {
        return resourceDirectory;
    }

    public List<String> getSupportFiles() {
        return supportFiles;
    }

    // name of the tester file this question should produce > matches TesterLoader discovery glob
    public String getTesterFileName() {
        return id + "Tester.java";
    }

    // main class the grader runs > filename without .java
    public String getTesterMainClass() {
        return id + "Tester";
    }

    public boolean hasPdfExcerpt() {
        return !pdfExcerpt.isBlank();
    }

    @Override
    public String toString() {
        return "QuestionSpec{id=" + id + ", folder=" + folder
                + ", answerTemplateFile=" + answerTemplateFile
                + ", templateSourceLength=" + answerTemplateSource.length()
                + ", pdfExcerptLength=" + pdfExcerpt.length() + '}';
    }
}
