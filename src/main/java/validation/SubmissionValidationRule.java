package validation;

import java.util.List;

import assessment.AssessmentDefinition;
import submission.StudentSubmission;

/// defines validation checks spanning a student submission
public interface SubmissionValidationRule { 
    List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment, ValidationFiles files);
}
