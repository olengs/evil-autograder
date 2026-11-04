package generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import config.AssessmentLoader;
import model.AssessmentDefinition;
import model.QuestionDefinition;

public class QuestionSpecCollector { //turns answer-template folders into one QuestionSpec per question
    public List<QuestionSpec> collect(Path resourcesDirectory) throws IOException {
        // reuse the grader's own discovery so the generated testers match exactly what will be graded
        AssessmentDefinition assessment = AssessmentLoader.load(resourcesDirectory);

        List<QuestionSpec> specs = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            // the template source is the authoritative method signature for the llm
            String templateSource = Files.readString(resolveTemplateFile(question));

            // carry the question folder & its support files so validation can compile like the real grader
            specs.add(new QuestionSpec(question.getId(), templateSource,
                    question.getResourceDirectory(), question.getAllowedResourceFiles()));
        }
        return specs;
    }

    // the template file is the only answer file for this question > folder/id.java e.g. Q1/Q1a.java
    private Path resolveTemplateFile(QuestionDefinition question) throws IOException {
        List<String> required = question.getRequiredFiles();
        if (required.isEmpty()) {
            throw new IOException("no answer template file for question " + question.getId());
        }
        return question.getResourceDirectory().resolve(required.get(0));
    }
}
