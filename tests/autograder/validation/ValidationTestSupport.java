// Provides temporary fixtures and assertions for regression tests.
// Testing only; not required by the application.

package autograder.validation;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.Student;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.rules.AnswerFileRule;
import autograder.validation.rules.StudentHeaderRule;
import autograder.validation.rules.UnexpectedFileRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class ValidationTestSupport implements AutoCloseable {
    static final Student STUDENT = new Student("Alice", "alice", "alice@school.edu");
    static final String HEADER = "// Name: Alice\n// Email: alice@school.edu\n";
    final Path root;

    ValidationTestSupport() throws IOException {
        root = Files.createTempDirectory("validation-test-");
    }

    StudentSubmission submission() {
        return new StudentSubmission(STUDENT, root.resolve("alice.zip"), root);
    }

    Path write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    static QuestionDefinition question(String id, String... required) {
        return new QuestionDefinition(id, "Q1", List.of(required));
    }

    static AssessmentDefinition assessment(QuestionDefinition... questions) {
        return new AssessmentDefinition("test", List.of(questions));
    }

    static SubmissionValidator validator(AnswerFileLocator locator) {
        return new SubmissionValidator(List.of(new AnswerFileRule(locator), new StudentHeaderRule(locator)),
                List.of(new UnexpectedFileRule()), locator);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static SubmissionIssue onlyIssue(List<SubmissionIssue> issues, String code) {
        check(issues.size() == 1, "expected one " + code + " issue, got " + issues);
        SubmissionIssue issue = issues.get(0);
        check(issue.getCode().equals(code), "unexpected issue code: " + issue);
        return issue;
    }

    static void checkError(SubmissionIssue issue, String questionId, Path affected) {
        check(issue.getSeverity() == IssueSeverity.ERROR, "expected ERROR severity");
        check(questionId.equals(issue.getQuestionId()), "wrong question scope");
        check(affected.equals(issue.getAffectedPath()), "wrong affected path");
    }

    @Override
    public void close() throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
