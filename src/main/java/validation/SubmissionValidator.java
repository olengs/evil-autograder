package validation;

import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import utils.FileUtils;

public class SubmissionValidator { //runs validation rules & builds one ~questionattempt~ for each question
    private final List<ValidationRule> questionRules;
    private final List<SubmissionValidationRule> submissionRules;
    private final SubmissionFileFinder fileLocator;

    public SubmissionValidator(List<ValidationRule> questionRules, List<SubmissionValidationRule> submissionRules, SubmissionFileFinder fileLocator) {
        this.questionRules = questionRules;
        this.submissionRules = submissionRules;
        this.fileLocator = fileLocator;
    }

    public StudentSubmission validate(StudentSubmission submission, AssessmentDefinition assessment) {
        if (!submission.getQuestionAttempts().isEmpty()) {
            throw new IllegalStateException("submission already has question attempts");
        }

        // Check paths before invoking rules, so no rule can read outside this submission.
        try {
            for (var question : assessment.getQuestions()) {
                Path folder = FileUtils.resolveContained(submission.getRootDirectory(), question.getExpectedFolder());
                for (String name : question.getRequiredFiles()) FileUtils.resolveContained(folder, name);
                for (String name : question.getEditableFiles()) FileUtils.resolveContained(folder, name);
                for (String name : question.getAllowedResourceFiles()) FileUtils.resolveContained(folder, name);
            }
        } catch (IOException | RuntimeException error) {
            submission.addIssues(List.of(new SubmissionIssue("invalid submission path", IssueSeverity.ERROR,
                    error.getMessage(), null, submission.getRootDirectory())));
            return submission;
        }

        //@1ejun6 > step 1 > rules that need to inspect entire submission
        for (SubmissionValidationRule rule : submissionRules) {
            try {
                submission.addIssues(rule.validate(submission, assessment));
            } catch (UncheckedIOException | SecurityException error) {
                submission.addIssues(List.of(fileAccessIssue(rule.getClass().getSimpleName(), error,
                        null, submission.getRootDirectory())));
            }
        }

        //@1ejun6 > step 2 > validate every question
        List<SubmissionIssue> upstreamIssues = submission.getSubmissionIssues();
        for (QuestionDefinition question : assessment.getQuestions()) {
            validateQuestion(submission, question, upstreamIssues);
        }
        return submission;
    }

    private void validateQuestion(StudentSubmission submission, QuestionDefinition question, List<SubmissionIssue> upstreamIssues) {
        // Preserve normalization and submission-level findings on each relevant attempt.
        List<SubmissionIssue> inherited = upstreamIssues.stream()
                .filter(issue -> issue.getQuestionId() == null || issue.getQuestionId().equals(question.getId())).toList();
        List<SubmissionIssue> issues = new ArrayList<>(inherited);
        Path questionDirectory = submission.getRootDirectory().resolve(question.getExpectedFolder()).toAbsolutePath().normalize();
        for (ValidationRule rule : questionRules) {
            try {
                issues.addAll(rule.validate(submission, question));
            } catch (UncheckedIOException | SecurityException error) {
                issues.add(fileAccessIssue(rule.getClass().getSimpleName(), error, question.getId(), questionDirectory));
            }
        }

        List<Path> submittedFiles = locateFiles(submission, question, issues);
        QuestionAttempt attempt = new QuestionAttempt(question, questionDirectory, submittedFiles, issues);

        submission.addQuestionAttempt(attempt);
        submission.addIssues(issues.subList(inherited.size(), issues.size()));
    }

    private List<Path> locateFiles(StudentSubmission submission, QuestionDefinition question, List<SubmissionIssue> issues) {
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        
        try {
            return fileLocator.findSubmissionFiles(questionFolder, question.getEditableFiles());
        } catch (IOException | UncheckedIOException | SecurityException e) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to locate answer files > " + e.getMessage(), question.getId(), questionFolder));
            return new ArrayList<>();
        }
    }

    private static SubmissionIssue fileAccessIssue(String rule, RuntimeException error, String questionId, Path path) {
        return new SubmissionIssue("validation file access error", IssueSeverity.ERROR,
                "Unable to inspect files in " + rule + ": " + error.getMessage(), questionId, path);
    }
}
