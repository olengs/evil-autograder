package validation.rules;

import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import utils.SubmissionFileFinder;
import validation.ValidationRule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
public class RequiredFileRule implements ValidationRule { //reports required answer files that cannot be found
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, SubmissionFileFinder files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder()); //@1ejun6 > builds question folder path

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> caseInsensitiveMatch = files.findAnswerFile(submission.getRootDirectory(), question.getExpectedFolder(), requiredFile);
                if (caseInsensitiveMatch.isPresent()) {
                    continue;
                }

                issues.add(new SubmissionIssue("missing required file", IssueSeverity.ERROR, "required file " + requiredFile + " is missing.", question.getId(), questionFolder.resolve(requiredFile)));
            } catch (IOException error) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to inspect " + questionFolder + " > " + error.getMessage(), question.getId(), questionFolder));
            }
        }
        return issues;
    }
}
