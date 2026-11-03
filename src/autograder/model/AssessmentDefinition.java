// Describes one assessment and its question definitions.
// Stores the assessment name and read-only question list.

package autograder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssessmentDefinition {
    private final String name;
    private final List<QuestionDefinition> questions;

    public AssessmentDefinition(String name, List<QuestionDefinition> questions) {
        this.name = name;
        this.questions = new ArrayList<>(questions);
    }

    public String getName() {
        return name;
    }

    public List<QuestionDefinition> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
