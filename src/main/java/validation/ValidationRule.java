package validation;

import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import java.util.List;

//once per question

public interface ValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question);
}
