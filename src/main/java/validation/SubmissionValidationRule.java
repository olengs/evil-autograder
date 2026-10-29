package validation;

import model.AssessmentDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import java.util.List;

//once per submission

public interface SubmissionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment);
}
