package validation;

import assessment.QuestionDefinition;
import java.util.List;
import submission.StudentSubmission;

public interface QuestionValidationRule { //defines validation checks for one student question
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, ValidationFiles.QuestionFiles files);
}
