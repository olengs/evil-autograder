package validation;

import model.AssessmentDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import java.util.List;

public interface SubmissionValidationRule { //defines the validate method used by rules that check a whole submission
    List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment);
}
