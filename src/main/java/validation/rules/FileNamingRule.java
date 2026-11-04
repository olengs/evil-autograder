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

public class FileNamingRule implements ValidationRule { //checks that answer filenames use expected capital letters
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, SubmissionFileFinder files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> caseInsensitiveMatch = files.findAnswerFile(submission.getRootDirectory(), question.getExpectedFolder(), requiredFile);
                if (caseInsensitiveMatch.isPresent()) {
                    Path actualFile = caseInsensitiveMatch.get();
                    if (!actualFile.equals(questionFolder.resolve(requiredFile))) {
                        issues.add(new SubmissionIssue("wrong file name", IssueSeverity.WARNING, "answer will be copied as " + question.getExpectedFolder() + "/" + requiredFile + " > found " + submission.getRootDirectory().relativize(actualFile), question.getId(), actualFile));
                    }
                }
            } catch (IOException error) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to inspect " + questionFolder + " > " + error.getMessage(), question.getId(), questionFolder));
            }
        }
        return issues;
    }
}
