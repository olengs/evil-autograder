package validation.rules;

import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.ValidationRule;
import utils.SubmissionFileFinder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestionFolderRule implements ValidationRule { //check that student supplied folder required for a question
    @Override //@1ejun6 > checks one student submission against one question requirements & returns any issues found
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, SubmissionFileFinder files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path expectedFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        try {
            var actual = files.findQuestionFolder(submission.getRootDirectory(), question.getExpectedFolder());
            if (actual.isPresent() && actual.get().getFileName().toString().equals(question.getExpectedFolder())) return issues;
            boolean recoverable = !files.findAnswerFiles(submission.getRootDirectory(), question.getExpectedFolder(), question.getRequiredFiles()).isEmpty();
            issues.add(new SubmissionIssue(recoverable ? "question folder layout" : "missing question folder", recoverable ? IssueSeverity.WARNING : IssueSeverity.ERROR, recoverable ? "answers found outside expected folder spelling > copied only when grading" : "expected folder " + question.getExpectedFolder() + " is missing", question.getId(), expectedFolder));
        } catch (IOException error) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, error.getMessage(), question.getId(), expectedFolder));
        }
        return issues;
    }
}
