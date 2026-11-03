// Defines the contract for validating one question.
// Returns question-specific issues for a student submission.

package autograder.validation;

import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import java.util.List;

public interface QuestionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question);
}
