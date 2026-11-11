package validation;

import java.util.List;

import assessment.QuestionDefinition;
import submission.StudentSubmission;

/// defines validation checks for one student question
public interface QuestionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, ValidationFiles.QuestionFiles files);
}
