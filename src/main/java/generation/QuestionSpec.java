package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import assessment.AssessmentLoader;
import assessment.QuestionDefinition;

/// Stores question's resources for generated templates
public record QuestionSpec(String id, String answerTemplateSource, Path resourceDirectory, List<String> supportFiles) {
    public QuestionSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("question id is required");
        if (answerTemplateSource == null || answerTemplateSource.isBlank()) {
            throw new IllegalArgumentException("answerTemplateSource is required for " + id);
        }
        supportFiles = supportFiles == null ? List.of() : List.copyOf(supportFiles);
    }

    public static List<QuestionSpec> collect(Path resourcesDirectory) throws IOException {
        // Use the grader's discovery to preserve question IDs and order.
        var assessment = AssessmentLoader.load(resourcesDirectory);
        List<QuestionSpec> specs = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            var answers = question.getRequiredAnswerFiles();
            if (answers.isEmpty()) throw new IOException("no answer template file for question " + question.getId());
            String template = Files.readString(question.getResourceDirectory().resolve(answers.getFirst()));
            specs.add(new QuestionSpec(question.getId(), template, question.getResourceDirectory(), question.getSupportFiles()));
        }
        return specs;
    }

    public String folderName() {
        return resourceDirectory == null || resourceDirectory.getFileName() == null
                ? id : resourceDirectory.getFileName().toString();
    }

    public String testerFileName() {
        return testerMainClass() + ".java";
    }

    public String testerMainClass() {
        return id + "Tester";
    }
}
