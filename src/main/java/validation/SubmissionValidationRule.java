//defines validation contract for checks spanning a student submission & its assessment
//returns findings that submissionvalidator collects before building question attempts

package validation;

import assessment.AssessmentDefinition;
import java.util.List;
import submission.StudentSubmission;

public interface SubmissionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment, ValidationFiles files);
}
