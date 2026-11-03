// Tests required folders, answer presence, naming, and read errors.
// Testing only; not required by the application.

package autograder.validation;

import autograder.model.QuestionDefinition;
import autograder.model.SubmissionIssue;
import autograder.validation.rules.AnswerFileRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static autograder.validation.ValidationTestSupport.*;

public class AnswerFileRuleTest {
    public static void main(String[] args) throws Exception {
        missingFolderDoesNotProduceMissingFileErrors();
        missingFileReportsExpectedPath();
        wrongCapitalizationReportsActualFilename();
        validFilesAreAcceptedWithoutChangingContents();
        failedLookupProducesOneStructuralReadError();
        System.out.println("AnswerFileRuleTest: 5 tests passed");
    }

    private static void missingFolderDoesNotProduceMissingFileErrors() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            AnswerFileLocator neverSearch = new AnswerFileLocator() {
                @Override
                public Optional<Path> findCandidate(Path directory, String required) {
                    throw new AssertionError("missing folder must stop answer lookup");
                }
            };
            QuestionDefinition question = question("Q1a", "Answer.java", "Helper.java");
            SubmissionIssue issue = onlyIssue(new AnswerFileRule(neverSearch)
                    .validate(fixture.submission(), question), "missing question folder");
            checkError(issue, "Q1a", fixture.root.resolve("Q1"));
            check(issue.getMessage().equals("expected folder Q1 is missing"), "folder message changed");
        }
    }

    private static void missingFileReportsExpectedPath() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Files.createDirectory(fixture.root.resolve("Q1"));
            SubmissionIssue issue = onlyIssue(new AnswerFileRule(new AnswerFileLocator())
                    .validate(fixture.submission(), question("Q1a", "Answer.java")), "missing required file");
            checkError(issue, "Q1a", fixture.root.resolve("Q1/Answer.java"));
            check(issue.getMessage().equals("required file Answer.java is missing."), "missing message changed");
        }
    }

    private static void wrongCapitalizationReportsActualFilename() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            fixture.write("Q1/answer.java", "class Answer {}\n");
            SubmissionIssue issue = onlyIssue(new AnswerFileRule(new AnswerFileLocator())
                    .validate(fixture.submission(), question("Q1a", "Answer.java")), "wrong file name");
            checkError(issue, "Q1a", fixture.root.resolve("Q1/answer.java"));
            check(issue.getAffectedPath().getFileName().toString().equals("answer.java"),
                    "actual spelling must be preserved on Windows");
            check(issue.getMessage().equals("expected filename Answer.java but found answer.java."),
                    "filename message changed");
        }
    }

    private static void validFilesAreAcceptedWithoutChangingContents() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path answer = fixture.write("Q1/Answer.java", "class Answer {}\n");
            Path helper = fixture.write("Q1/Helper.java", "class Helper {}\n");
            List<SubmissionIssue> issues = new AnswerFileRule(new AnswerFileLocator())
                    .validate(fixture.submission(), question("Q1a", "Answer.java", "Helper.java"));
            check(issues.isEmpty(), "exact required files should pass");
            check(Files.readString(answer).equals("class Answer {}\n"), "answer was modified");
            check(Files.readString(helper).equals("class Helper {}\n"), "helper was modified");
        }
    }

    private static void failedLookupProducesOneStructuralReadError() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Files.createDirectory(fixture.root.resolve("Q1"));
            AnswerFileLocator ambiguous = new AnswerFileLocator() {
                @Override
                public Optional<Path> findIgnoringCase(Path directory, String required) throws IOException {
                    throw new IOException("ambiguous filename > " + required);
                }
            };
            SubmissionIssue issue = onlyIssue(new AnswerFileRule(ambiguous)
                    .validate(fixture.submission(), question("Q1a", "Answer.java")), "file read error");
            checkError(issue, "Q1a", fixture.root.resolve("Q1"));
            check(issue.getMessage().contains("ambiguous filename > Answer.java"), "read cause was lost");
        }
    }
}
