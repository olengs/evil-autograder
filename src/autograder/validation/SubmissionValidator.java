// Coordinates submission-wide and per-question validation.
// Collects findings and creates question attempts with answer paths.

package autograder.validation;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.utils.StudentHeaderReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SubmissionValidator {
    private final List<QuestionValidationRule> questionRules;
    private final List<SubmissionValidationRule> submissionRules;
    private final AnswerFileLocator fileLocator;

    public SubmissionValidator(List<QuestionValidationRule> questionRules, List<SubmissionValidationRule> submissionRules, AnswerFileLocator fileLocator) {
        this.questionRules = questionRules;
        this.submissionRules = submissionRules;
        this.fileLocator = fileLocator;
    }

    public StudentSubmission validate(StudentSubmission submission, AssessmentDefinition assessment) {
        if (!submission.getQuestionAttempts().isEmpty()) {
            throw new IllegalStateException("submission already has question attempts");
        }

        //step 1 > rules that need to inspect entire submission
        for (SubmissionValidationRule rule : submissionRules) {
            List<SubmissionIssue> issues = rule.validate(submission, assessment);
            submission.addIssues(issues);
        }

        //step 2 > validate every question
        for (QuestionDefinition question : assessment.getQuestions()) {
            validateQuestion(submission, question);
        }
        return submission;
    }

    private void validateQuestion(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        
        //run all question level validation rules
        for (QuestionValidationRule rule : questionRules) {
            List<SubmissionIssue> ruleIssues = rule.validate(submission, question);
            issues.addAll(ruleIssues);
        }

        //locate submitted answer files
        List<Path> answerFiles = locateSubmittedAnswerFiles(submission, question, issues);

        //build ~questionattempt~
        QuestionAttempt attempt = new QuestionAttempt(question, answerFiles, issues);
        submission.addQuestionAttempt(attempt);
        submission.addIssues(issues);
    }

    private List<Path> locateSubmittedAnswerFiles(StudentSubmission submission, QuestionDefinition question, List<SubmissionIssue> issues) {
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        
        try {
            return fileLocator.locateAnswerFiles(questionFolder, selectEditableFileNames(question));
        } catch (IOException e) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                    "unable to locate answer files > " + e.getMessage(), question.getId(), questionFolder));
            return new ArrayList<>();
        }
    }

    // Required answers plus declared Java companions with an identity section in the trusted template.
    private List<String> selectEditableFileNames(QuestionDefinition question) throws IOException {
        List<String> files = new ArrayList<>(question.getRequiredFiles());
        Path resources = question.getResourceDirectory();
        if (resources == null) return files;
        for (String resource : question.getAllowedResourceFiles()) {
            if (resource.endsWith(".java") && StudentHeaderReader.hasHeaderSection(resources.resolve(resource))) {
                files.add(resource);
            }
        }
        return files;
    }
}
