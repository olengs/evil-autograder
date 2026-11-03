// Tests rule execution, issue ownership, and attempt validity.
// Testing only; not required by the application.

package autograder.validation;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.rules.StudentHeaderRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static autograder.validation.ValidationTestSupport.*;

public class ValidationWorkflowTest {
    public static void main(String[] args) throws Exception {
        sharedFolderUnionAllowsSiblingAnswersAndResources();
        warningsAndErrorsReachTheirExistingCollections();
        revalidationIsRejectedBeforeAddingDuplicateIssues();
        headersRemainRequiredOnlyExactOnlyAndLimitedToFifteenLines();
        missingQuestionDoesNotStopLaterQuestions();
        submissionIssuesAreNotAutomaticallyInheritedByAttempts();
        System.out.println("ValidationWorkflowTest: 6 tests passed");
    }

    private static void sharedFolderUnionAllowsSiblingAnswersAndResources() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path first = fixture.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            Path second = fixture.write("Q1/Second.java", HEADER + "class Second {}\n");
            fixture.write("Q1/helper.txt", "resource");
            Path extra = fixture.write("Q1/extra.txt", "extra");
            fixture.write("Q1/nested/ignored.txt", "shallow scan must remain unchanged");
            QuestionDefinition q1 = new QuestionDefinition("Q1a", "Q1", List.of("Answer.java"), List.of("helper.txt"));
            QuestionDefinition q2 = question("Q1b", "Second.java");
            StudentSubmission submission = fixture.submission();
            validator(new AnswerFileLocator()).validate(submission, assessment(q1, q2));
            SubmissionIssue warning = onlyIssue(submission.getSubmissionIssues(), "unexpected file");
            check(warning.getAffectedPath().equals(extra), "union falsely flagged sibling answer or resource");
            check(warning.getQuestionId() == null && warning.getSeverity() == IssueSeverity.WARNING,
                    "unexpected file must remain a submission-level warning");
            check(submission.getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid),
                    "unexpected files must not block question attempts");
            check(submission.getQuestionAttempts().get(0).getAnswerFiles().equals(List.of(first)), "first answer lost");
            check(submission.getQuestionAttempts().get(1).getAnswerFiles().equals(List.of(second)), "second answer lost");
            check(Files.readString(extra).equals("extra"), "unexpected file was altered");
        }
    }

    private static void warningsAndErrorsReachTheirExistingCollections() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            fixture.write("Q1/Answer.java", "class Answer {}\n");
            StudentSubmission submission = fixture.submission();
            AssessmentDefinition assessment = assessment(question("Q1a", "Answer.java"), question("Q1b", "Missing.java"));
            StudentSubmission returned = validator(new AnswerFileLocator()).validate(submission, assessment);
            check(returned == submission, "validator should enrich and return the same submission");
            QuestionAttempt first = submission.getQuestionAttempts().get(0);
            QuestionAttempt second = submission.getQuestionAttempts().get(1);
            SubmissionIssue warning = onlyIssue(first.getIssues(), "missing student header");
            SubmissionIssue error = onlyIssue(second.getIssues(), "missing required file");
            check(warning.getSeverity() == IssueSeverity.WARNING && first.isValid(), "warning blocked attempt");
            check(!second.isValid() && second.getAnswerFiles().isEmpty(), "missing answer should block second attempt");
            check(submission.getSubmissionIssues().equals(List.of(warning, error)), "question issue aggregation changed");
            check(first.getAnswerFiles().size() == 1, "warning prevented answer handoff");
        }
    }

    private static void revalidationIsRejectedBeforeAddingDuplicateIssues() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            fixture.write("Q1/Answer.java", "class Answer {}\n");
            StudentSubmission submission = fixture.submission();
            SubmissionValidator validator = validator(new AnswerFileLocator());
            AssessmentDefinition assessment = assessment(question("Q1a", "Answer.java"));
            validator.validate(submission, assessment);
            try {
                validator.validate(submission, assessment);
                throw new AssertionError("second validation should fail");
            } catch (IllegalStateException expected) {
                check(submission.getQuestionAttempts().size() == 1, "revalidation duplicated attempts");
                check(submission.getSubmissionIssues().size() == 1, "revalidation duplicated issues");
            }
        }
    }

    private static void headersRemainRequiredOnlyExactOnlyAndLimitedToFifteenLines() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            fixture.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            fixture.write("Q1/Helper.java", "class Helper {}\n");
            StudentHeaderRule headerRule = new StudentHeaderRule(new AnswerFileLocator());
            QuestionDefinition question = new QuestionDefinition("Q1a", "Q1", List.of("Answer.java"), List.of("Helper.java"));
            check(headerRule.validate(fixture.submission(), question).isEmpty(), "resource received header check");
            fixture.write("Q1/Answer.java", "// introduction\n".repeat(15) + HEADER + "class Answer {}\n");
            onlyIssue(headerRule.validate(fixture.submission(), question), "missing student header");
            Files.delete(fixture.root.resolve("Q1/Answer.java"));
            fixture.write("Q1/answer.java", "class Answer {}\n");
            check(headerRule.validate(fixture.submission(), question).isEmpty(), "exact-only header lookup changed");
            StudentSubmission submission = fixture.submission();
            validator(new AnswerFileLocator()).validate(submission, assessment(question));
            onlyIssue(submission.getSubmissionIssues(), "wrong file name");
            check(submission.getQuestionAttempts().get(0).getAnswerFiles().get(0).getFileName().toString()
                    .equals("answer.java"), "wrong-case answer lost from handoff");
        }
    }

    private static void missingQuestionDoesNotStopLaterQuestions() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path answer = fixture.write("Q2/Answer.java", HEADER + "class Answer {}\n");
            QuestionDefinition q1 = question("Q1a", "Missing.java");
            QuestionDefinition q2 = new QuestionDefinition("Q2", "Q2", List.of("Answer.java"));
            StudentSubmission submission = fixture.submission();
            validator(new AnswerFileLocator()).validate(submission, assessment(q1, q2));
            onlyIssue(submission.getQuestionAttempts().get(0).getIssues(), "missing question folder");
            QuestionAttempt second = submission.getQuestionAttempts().get(1);
            check(second.isValid() && second.getAnswerFiles().equals(List.of(answer)), "later question did not continue");
        }
    }

    private static void submissionIssuesAreNotAutomaticallyInheritedByAttempts() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            fixture.write("Q1/Answer.java", HEADER + "class Answer {}\n");
            SubmissionIssue upstream = new SubmissionIssue("upstream", IssueSeverity.WARNING,
                    "pre-existing issue", null, fixture.root);
            SubmissionIssue globalError = new SubmissionIssue("folder read error", IssueSeverity.ERROR,
                    "simulated submission-level failure", null, fixture.root.resolve("Q1"));
            StudentSubmission submission = fixture.submission();
            submission.addIssues(List.of(upstream));
            AnswerFileLocator locator = new AnswerFileLocator();
            SubmissionValidator validator = new SubmissionValidator(
                    List.of(new autograder.validation.rules.AnswerFileRule(locator), new StudentHeaderRule(locator)),
                    List.of((studentSubmission, assessment) -> List.of(globalError)), locator);
            validator.validate(submission, assessment(question("Q1a", "Answer.java")));
            check(submission.getSubmissionIssues().equals(List.of(upstream, globalError)),
                    "submission issues should be preserved centrally");
            QuestionAttempt attempt = submission.getQuestionAttempts().get(0);
            check(attempt.getIssues().isEmpty() && attempt.isValid(),
                    "baseline does not automatically copy global errors into question attempts");
        }
    }
}
