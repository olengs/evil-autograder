package assessment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssessmentDefinition { //stores ordered questions in an assessment
    private final List<QuestionDefinition> questions;

    public AssessmentDefinition(List<QuestionDefinition> questions) {
        this.questions = new ArrayList<>(questions);
    }

    public List<QuestionDefinition> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
