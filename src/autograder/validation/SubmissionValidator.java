package autograder.validation;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;

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
        if (submission.getSubmissionIssues().stream().noneMatch(issue -> issue.getSeverity() == IssueSeverity.ERROR
                && issue.getQuestionId() == null)) {
            for (SubmissionValidationRule rule : submissionRules) {
                try {
                    submission.addIssues(rule.validate(submission, assessment));
                } catch (RuntimeException error) {
                    submission.addIssues(List.of(new SubmissionIssue("validation error", IssueSeverity.ERROR,
                            error.toString(), null, submission.getRootDirectory())));
                }
            }
        }

        //step 2 > validate every question
        for (QuestionDefinition question : assessment.getQuestions()) {
            List<SubmissionIssue> blocking = submission.getSubmissionIssues().stream()
                    .filter(issue -> issue.getSeverity() == IssueSeverity.ERROR
                            && (issue.getQuestionId() == null || issue.getQuestionId().equals(question.getId())))
                    .toList();
            if (blocking.isEmpty()) {
                validateQuestion(submission, question);
            } else {
                submission.addQuestionAttempt(new QuestionAttempt(question, List.of(), blocking));
            }
        }
        return submission;
    }

    private void validateQuestion(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        
        //run all question level validation rules
        for (ValidationRule rule : questionRules) {
            try {
                issues.addAll(rule.validate(submission, question));
            } catch (RuntimeException error) {
                issues.add(new SubmissionIssue("validation error", IssueSeverity.ERROR,
                        error.toString(), question.getId(), submission.getRootDirectory()));
            }
        }

        //locate submitted answer files
        List<Path> answerFiles = locateFiles(submission, question, issues);

        //build ~questionattempt~
        QuestionAttempt attempt = new QuestionAttempt(question, answerFiles, issues);
        submission.addQuestionAttempt(attempt);
        submission.addIssues(issues);
    }

    private List<Path> locateFiles(StudentSubmission submission, QuestionDefinition question, List<SubmissionIssue> issues) {
        Path questionFolder = submission.getRootDirectory();
        
        try {
            questionFolder = questionFolder.resolve(question.getExpectedFolder());
            return fileLocator.locateAnswerFiles(questionFolder, question.getRequiredFiles());
        } catch (IOException | RuntimeException e) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                    "unable to locate answer files > " + e.getMessage(), question.getId(), questionFolder));
            return new ArrayList<>();
        }
    }
}
