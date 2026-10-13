package autograder.validation;

import autograder.model.AssessmentDefinition;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SubmissionValidator {

    private final List<ValidationRule>
            questionRules;

    private final List<SubmissionValidationRule>
            submissionRules;

    private final AnswerFileLocator
            fileLocator;

    public SubmissionValidator(
            List<ValidationRule> questionRules,
            List<SubmissionValidationRule>
                    submissionRules,
            AnswerFileLocator fileLocator) {

        this.questionRules =
                questionRules;

        this.submissionRules =
                submissionRules;

        this.fileLocator =
                fileLocator;
    }

    public StudentSubmission validate(
            StudentSubmission submission,
            AssessmentDefinition assessment) {

        /*
         * STEP 1:
         * Rules that need to inspect
         * the entire submission.
         */
        for (SubmissionValidationRule rule :
                submissionRules) {

            List<SubmissionIssue> issues =
                    rule.validate(
                            submission,
                            assessment);

            submission.addIssues(
                    issues);
        }

        /*
         * STEP 2:
         * Validate every question.
         */
        for (QuestionDefinition question :
                assessment.getQuestions()) {

            validateQuestion(
                    submission,
                    question);
        }

        return submission;
    }

    private void validateQuestion(
            StudentSubmission submission,
            QuestionDefinition question) {

        List<SubmissionIssue> issues =
                new ArrayList<>();

        /*
         * Run all question-level
         * validation rules.
         */
        for (ValidationRule rule :
                questionRules) {

            List<SubmissionIssue>
                    ruleIssues =
                    rule.validate(
                            submission,
                            question);

            issues.addAll(
                    ruleIssues);
        }

        /*
         * Locate submitted answer files.
         */
        List<Path> answerFiles =
                locateFiles(
                        submission,
                        question,
                        issues);

        /*
         * Build the QuestionAttempt.
         */
        QuestionAttempt attempt =
                new QuestionAttempt(
                        question,
                        answerFiles,
                        issues);

        submission.addQuestionAttempt(
                attempt);

        submission.addIssues(
                issues);
    }

    private List<Path> locateFiles(
            StudentSubmission submission,
            QuestionDefinition question,
            List<SubmissionIssue> issues) {

        Path questionFolder =
                submission
                        .getRootDirectory()
                        .resolve(
                                question
                                    .getExpectedFolder());

        try {

            return fileLocator
                    .locateAnswerFiles(
                            questionFolder,
                            question
                                .getRequiredFiles());

        } catch (IOException e) {

            return new ArrayList<>();
        }
    }
}