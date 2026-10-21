package validation;

import assessment.AssessmentDefinition;
import java.util.List;
import submission.StudentSubmission;

public interface SubmissionValidationRule { //defines validation checks spanning a student submission
    List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment, ValidationFiles files);
}
