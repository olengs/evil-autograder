// Defines the validation contract for a whole student submission.
// Returns issues that apply across the assessment rather than to one question.

package validation;

import model.AssessmentDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import java.util.List;

public interface SubmissionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment);
}
