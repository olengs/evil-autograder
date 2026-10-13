package autograder.validation;

import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import java.util.List;

//once per question

public interface ValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question);
}
