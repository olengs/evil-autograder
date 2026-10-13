package autograder.validation.rules;

import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.ValidationRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

//checks required files

public class QuestionFolderRule implements ValidationRule {
    @Override //checks one student submission against one question requirements &&& returns any issues found
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path expectedFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        
        if (!Files.isDirectory(expectedFolder)) {
            issues.add(new SubmissionIssue("missing question folder", IssueSeverity.ERROR,
                    "expected folder " + question.getExpectedFolder() + " is missing", question.getId(),
                    expectedFolder));
        }
        return issues;
    }
}