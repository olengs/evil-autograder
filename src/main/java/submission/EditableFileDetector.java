package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import model.AssessmentDefinition;
import model.QuestionDefinition;
import model.StudentSubmission;

public class EditableFileDetector { //works out which template files students may change from their name & email comment section
    private static final String JAVA_EXTENSION = ".java";

    /**
     * a template file is editable when the template \ any submission has a comment section in it
     * the union keeps a file editable when some students delete their comment section
     * only template files qualify > a students own helper file cannot unlock anything for the class
     *
     * @param assessment > questions read from the template
     * @param submissions > normalized submissions
     * @return > same questions with their editable files filled in
     * @throws IOException > if a template file cannot be read
     */
    public static AssessmentDefinition detect(AssessmentDefinition assessment, List<StudentSubmission> submissions) throws IOException {
        List<QuestionDefinition> questions = new ArrayList<>();

        for (QuestionDefinition question : assessment.getQuestions()) {
            Set<String> editable = new LinkedHashSet<>(question.getRequiredFiles());

            for (String resource : question.getAllowedResourceFiles()) {
                if (resource.endsWith(JAVA_EXTENSION) && (hasCommentSection(question, resource) || anyHasCommentSection(submissions, question, resource))) {
                    editable.add(resource);
                }
            }
            questions.add(question.withEditableFiles(List.copyOf(editable)));
        }
        return new AssessmentDefinition(assessment.getName(), questions);
    }

    private static boolean hasCommentSection(QuestionDefinition question, String resource) throws IOException {
        return StudentHeaderNormalizer.hasCommentSection(question.getResourceDirectory().resolve(resource));
    }

    private static boolean anyHasCommentSection(List<StudentSubmission> submissions, QuestionDefinition question, String resource) {
        for (StudentSubmission submission : submissions) {
            Path file = submission.getRootDirectory().resolve(question.getExpectedFolder()).resolve(resource);
            try {
                if (Files.isRegularFile(file) && StudentHeaderNormalizer.hasCommentSection(file)) {
                    return true;
                }
            } catch (IOException error) {
                continue; //one unreadable submission only means one less sample
            }
        }
        return false;
    }
}
