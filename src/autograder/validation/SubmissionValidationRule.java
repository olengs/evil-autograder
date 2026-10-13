package autograder.validation;

import autograder.model.AssessmentDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;

import java.util.List;

public interface SubmissionValidationRule {

    List<SubmissionIssue> validate(
            StudentSubmission submission,
            AssessmentDefinition assessment);
}