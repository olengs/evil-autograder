package generation.questionSpec;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Files;
import assessment.AssessmentLoader;
import assessment.QuestionDefinition;

public final class QuestionSpec { //everything the llm needs to write one tester > id & template signature
    private final String id; //question id & answer class name > e.g. Q1a or Q3
    private final String answerTemplateSource; //full source of the template > gives the exact method signature
    private final Path resourceDirectory; //the question folder holding support files > e.g. .../Q2
    private final List<String> supportFiles; //support files needed to compile & run > e.g. DataException.class

    public QuestionSpec(String id, String answerTemplateSource, Path resourceDirectory, List<String> supportFiles) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("question id is required");
        }
        if (answerTemplateSource == null || answerTemplateSource.isBlank()) {
            throw new IllegalArgumentException("answerTemplateSource is required for " + id);
        }
        this.id = id;
        this.answerTemplateSource = answerTemplateSource;
        this.resourceDirectory = resourceDirectory;
        //copy defensively > empty when the question has no support files
        this.supportFiles = supportFiles == null ? List.of() : List.copyOf(supportFiles);
    }

    public static List<QuestionSpec> collect(Path resourcesDirectory) throws IOException {
        //reuse the grader's own discovery so the generated testers match exactly what will be graded
        var assessment = AssessmentLoader.load(resourcesDirectory);

        List<QuestionSpec> specs = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            //the template source is the authoritative method signature for the llm
            String templateSource = Files.readString(resolveTemplateFile(question));

            //carry the question folder & its support files so validation can compile like the real grader
            specs.add(new QuestionSpec(question.getId(), templateSource,
                    question.getResourceDirectory(), question.getSupportFiles()));
        }
        return specs;
    }

    //the template file is the only answer file for this question > folder/id.java e.g. Q1/Q1a.java
    private static Path resolveTemplateFile(QuestionDefinition question) throws IOException {
        List<String> required = question.getRequiredAnswerFiles();
        if (required.isEmpty()) {
            throw new IOException("no answer template file for question " + question.getId());
        }
        return question.getResourceDirectory().resolve(required.get(0));
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

    //the question folder name > e.g. Q1 for Q1a & Q1b > the id when the spec has no folder
    public String getFolderName() {
        return resourceDirectory == null || resourceDirectory.getFileName() == null
                ? id
                : resourceDirectory.getFileName().toString();
    }

    //name of the tester file this question should produce > matches TesterLoader discovery glob
    public String getTesterFileName() {
        return id + "Tester.java";
    }

    //main class the grader runs > filename without .java
    public String getTesterMainClass() {
        return id + "Tester";
    }

    @Override
    public String toString() {
        return "QuestionSpec{id=" + id + ", resourceDirectory=" + resourceDirectory
                + ", templateSourceLength=" + answerTemplateSource.length() + '}';
    }
}
