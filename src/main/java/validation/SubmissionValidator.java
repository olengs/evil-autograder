package validation;

import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.rules.StudentHeaderRule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

        //@1ejun6 > step 1 > rules that need to inspect entire submission
        for (SubmissionValidationRule rule : submissionRules) {
            List<SubmissionIssue> issues = rule.validate(submission, assessment);
            submission.addIssues(issues);
        }

        //@1ejun6 > step 2 > validate every question
        for (QuestionDefinition question : assessment.getQuestions()) {
            validateQuestion(submission, question);
        }
        return submission;
    }

    private void validateQuestion(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        for (ValidationRule rule : questionRules) {
            issues.addAll(rule.validate(submission, question));
        }

        Path questionDirectory = submission.getRootDirectory().resolve(question.getExpectedFolder()).toAbsolutePath().normalize();
        List<Path> submittedFiles = locateFiles(submission, question, issues);
        QuestionAttempt attempt = new QuestionAttempt(question, questionDirectory, submittedFiles, issues);

        submission.addQuestionAttempt(attempt);
        submission.addIssues(issues);
    }

    private List<Path> locateFiles(StudentSubmission submission, QuestionDefinition question, List<SubmissionIssue> issues) {
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        
        try {
            return fileLocator.findSubmissionFiles(questionFolder, editableFiles(question));
        } catch (IOException e) {
            issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to locate answer files > " + e.getMessage(), question.getId(), questionFolder));
            return new ArrayList<>();
        }
    }

    //required answers & any template java file with a name \ email section > a missing optional file is just left out
    private List<String> editableFiles(QuestionDefinition question) throws IOException {
        List<String> files = new ArrayList<>(question.getRequiredFiles());
        for (String resource : question.getAllowedResourceFiles()) {
            if (resource.endsWith(".java") && StudentHeaderRule.hasHeaderSection(question.getResourceDirectory().resolve(resource))) {
                files.add(resource);
            }
        }
        return files;
    }
}