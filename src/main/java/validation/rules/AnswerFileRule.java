// Checks question folders and required answer files through the shared file finder.
// Reports missing answers and recoverable folder or filename differences.

package validation.rules;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import utils.SubmissionFileFinder;
import validation.QuestionValidationRule;

public class AnswerFileRule implements QuestionValidationRule {
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question,
            SubmissionFileFinder files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        checkQuestionFolder(submission, question, files, issues);
        for (String requiredFile : question.getRequiredFiles()) {
            checkRequiredAnswer(submission, question, requiredFile, files, issues);
        }
        return issues;
    }

    private void checkQuestionFolder(StudentSubmission submission, QuestionDefinition question,
            SubmissionFileFinder files, List<SubmissionIssue> issues) {
        Path expectedFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        try {
            Optional<Path> actual = files.findQuestionFolder(submission.getRootDirectory(), question.getExpectedFolder());
            if (actual.isPresent() && actual.get().getFileName().toString().equals(question.getExpectedFolder())) return;
            boolean recoverable = !files.findAnswerFiles(submission.getRootDirectory(),
                    question.getExpectedFolder(), question.getRequiredFiles()).isEmpty();
            issues.add(new SubmissionIssue(recoverable ? "question folder layout" : "missing question folder",
                    recoverable ? IssueSeverity.WARNING : IssueSeverity.ERROR,
                    recoverable ? "answers found outside expected folder spelling > copied only when grading"
                            : "expected folder " + question.getExpectedFolder() + " is missing",
                    question.getId(), expectedFolder));
        } catch (IOException error) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                    error.getMessage(), question.getId(), expectedFolder));
        }
    }

    private void checkRequiredAnswer(StudentSubmission submission, QuestionDefinition question, String requiredFile,
            SubmissionFileFinder files, List<SubmissionIssue> issues) {
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        try {
            Optional<Path> answer = files.findAnswerFile(submission.getRootDirectory(),
                    question.getExpectedFolder(), requiredFile);
            // Compare spelling explicitly because Windows Path.equals ignores capitalization.
            if (answer.isEmpty()) {
                issues.add(new SubmissionIssue("missing required file", IssueSeverity.ERROR,
                        "required file " + requiredFile + " is missing.", question.getId(), questionFolder.resolve(requiredFile)));
            } else if (!answer.get().toString().equals(questionFolder.resolve(requiredFile).toString())) {
                issues.add(new SubmissionIssue("wrong file name", IssueSeverity.WARNING,
                        "answer will be copied as " + question.getExpectedFolder() + "/" + requiredFile
                                + " > found " + submission.getRootDirectory().relativize(answer.get()),
                        question.getId(), answer.get()));
            }
        } catch (IOException error) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                    "unable to inspect " + questionFolder + " > " + error.getMessage(), question.getId(), questionFolder));
        }
    }
}
