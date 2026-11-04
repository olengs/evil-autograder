// Defines the validation contract for one question.
// Uses the submission's shared file finder and returns question-specific issues.

package validation;

import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import utils.SubmissionFileFinder;
import java.util.List;

public interface QuestionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, SubmissionFileFinder files);
}
