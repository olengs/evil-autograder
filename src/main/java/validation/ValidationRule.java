package validation;

import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import java.util.List;

public interface ValidationRule { //defines validate method used by rules that check one question
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question);
}
