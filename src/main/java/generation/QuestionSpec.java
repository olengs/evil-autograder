package generation;

import assessment.AssessmentLoader;
import assessment.QuestionDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class QuestionSpec { //answer template & support files used for generation and validation
    private final String id;
    private final String answerTemplateSource;
    private final Path resourceDirectory;
    private final List<String> supportFiles;

    public QuestionSpec(String id, String answerTemplateSource, Path resourceDirectory, List<String> supportFiles) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("question id is required");
        if (answerTemplateSource == null || answerTemplateSource.isBlank()) throw new IllegalArgumentException("answerTemplateSource is required for " + id);
        this.id = id;
        this.answerTemplateSource = answerTemplateSource;
        this.resourceDirectory = resourceDirectory;
        this.supportFiles = supportFiles == null ? List.of() : List.copyOf(supportFiles);
    }

    public static List<QuestionSpec> collect(Path resourcesDirectory) throws IOException {
        // Use the grader's discovery to preserve question IDs and order.
        var assessment = AssessmentLoader.load(resourcesDirectory);

        List<QuestionSpec> specs = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            var answers = question.getRequiredAnswerFiles();
            if (answers.isEmpty()) throw new IOException("no answer template file for question " + question.getId());
            String templateSource = Files.readString(question.getResourceDirectory().resolve(answers.getFirst()));
            specs.add(new QuestionSpec(question.getId(), templateSource, question.getResourceDirectory(), question.getSupportFiles()));
        }
        return specs;
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
        return resourceDirectory == null || resourceDirectory.getFileName() == null ? id
                : resourceDirectory.getFileName().toString();
    }

    //name of the tester file this question should produce > matches TesterLoader discovery glob
    public String getTesterFileName() {
        return getTesterMainClass() + ".java";
    }

    //main class the grader runs > filename without .java
    public String getTesterMainClass() {
        return id + "Tester";
    }
}
