//defines validation contract for checking one question in a student submission
//uses shared question files & returns question-specific findings without modifying answer files

package validation;

import assessment.QuestionDefinition;
import java.util.List;
import submission.StudentSubmission;

public interface QuestionValidationRule {
    List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, ValidationFiles.QuestionFiles files);
}
