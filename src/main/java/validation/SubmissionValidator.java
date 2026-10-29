package validation;

import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SubmissionValidator {
    private final List<ValidationRule> questionRules;
    private final List<SubmissionValidationRule> submissionRules;
    private final AnswerFileLocator fileLocator;

    public SubmissionValidator(List<ValidationRule> questionRules, List<SubmissionValidationRule> submissionRules, AnswerFileLocator fileLocator) {
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
        for (ValidationRule rule : questionRules) {
            List<SubmissionIssue> ruleIssues = rule.validate(submission, question);
            issues.addAll(ruleIssues);
        }

        //locate submitted answer files
        List<Path> answerFiles = locateFiles(submission, question, issues);

        //build ~questionattempt~
        QuestionAttempt attempt = new QuestionAttempt(question, submission.getRootDirectory().resolve(question.getExpectedFolder()), answerFiles, issues);
        submission.addQuestionAttempt(attempt);
        submission.addIssues(issues);
    }

    private List<Path> locateFiles(StudentSubmission submission, QuestionDefinition question, List<SubmissionIssue> issues) {
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        
        try {
            return fileLocator.locateAnswerFiles(questionFolder, question.getRequiredFiles());
        } catch (IOException e) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                    "unable to locate answer files > " + e.getMessage(), question.getId(), questionFolder));
            return new ArrayList<>();
        }
    }
}
