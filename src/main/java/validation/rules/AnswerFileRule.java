//checks question folders & required answer files through shared lookup results
//reports missing answers as errors & recoverable folder or filename differences as warnings

package validation.rules;

import assessment.QuestionDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import submission.StudentSubmission;
import validation.QuestionValidationRule;
import validation.SubmissionIssue;
import validation.ValidationFiles;

public class AnswerFileRule implements QuestionValidationRule {
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, ValidationFiles.QuestionFiles files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        if (files.actualFolder().error() != null) {
            issues.add(SubmissionIssue.error("file read error", files.actualFolder().error().getMessage(), question.getId()));
            return issues;
        }
        checkQuestionFolder(question, files, issues);

        for (String requiredFile : files.requiredAnswers().keySet()) {
            checkRequiredAnswer(submission, question, requiredFile, files, issues);
        }
        return issues;
    }

    private void checkQuestionFolder(QuestionDefinition question, ValidationFiles.QuestionFiles files, List<SubmissionIssue> issues) {
        var actual = files.actualFolder().path();
        if (actual.isPresent() && actual.get().getFileName().toString().equals(question.getExpectedFolderName())) return;
        //lookup errors are reported with their required files > do not describe them as missing folders
        if (files.requiredAnswers().values().stream().anyMatch(lookup -> lookup.error() != null)) return;

        boolean recoverable = files.requiredAnswers().values().stream().anyMatch(lookup -> lookup.path().isPresent());
        if (recoverable) issues.add(SubmissionIssue.warning("question folder layout", "answers found outside expected folder spelling > copied only when grading", question.getId()));
        else issues.add(SubmissionIssue.error("missing question folder", "expected folder " + question.getExpectedFolderName() + " is missing", question.getId()));
    }

    private void checkRequiredAnswer(StudentSubmission submission, QuestionDefinition question, String requiredFile, ValidationFiles.QuestionFiles files, List<SubmissionIssue> issues) {
        Path questionFolder = files.expectedFolder();
        ValidationFiles.Lookup lookup = files.answer(requiredFile);
        if (lookup.error() != null) {
            issues.add(SubmissionIssue.error("file read error", "unable to inspect " + questionFolder + " > " + lookup.error().getMessage(), question.getId()));
            return;
        }
        var answer = lookup.path();
        //compare spelling explicitly because windows path.equals ignores capitalization
        if (answer.isEmpty()) issues.add(SubmissionIssue.error("missing required file", "required file " + requiredFile + " is missing.", question.getId()));
        else if (!answer.get().toString().equals(questionFolder.resolve(requiredFile).toString())) issues.add(SubmissionIssue.warning("wrong file name", "answer will be copied as " + question.getExpectedFolderName() + "/" + requiredFile + " > found " + submission.getRootDirectory().relativize(answer.get()), question.getId()));
    }
}
