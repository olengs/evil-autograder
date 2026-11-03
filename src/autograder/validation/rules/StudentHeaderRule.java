// Validates required-answer identity headers against the roster.
// Reports missing details and file-read failures.

package autograder.validation.rules;

import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.Student;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.utils.StudentHeaderReader;
import autograder.validation.AnswerFileLocator;
import autograder.validation.QuestionValidationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class StudentHeaderRule implements QuestionValidationRule {
    private final AnswerFileLocator fileLocator;

    public StudentHeaderRule(AnswerFileLocator fileLocator) {
        this.fileLocator = fileLocator;
    }

    /** A trusted template with a name or email section is intended to be edited. */
    public static boolean hasHeaderSection(Path file) throws IOException {
        return StudentHeaderReader.hasHeaderSection(file);
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        if (!Files.isDirectory(questionFolder)) {
            return issues;
        }

        Student student = submission.getStudent();

        if (student == null) {
            issues.add(new SubmissionIssue("unknown student", IssueSeverity.WARNING,
                    "cannot check headers without a matched student", question.getId(), questionFolder));
            return issues;
        }

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> file = fileLocator.findExact(questionFolder, requiredFile);

                if (file.isEmpty()) {
                    continue;
                }

                String header = StudentHeaderReader.readLeadingComments(file.get());

                boolean containsName = containsExpectedFieldValue(header, "Name", student.getName());
                boolean containsEmail = containsExpectedFieldValue(header, "Email(?:\\h+ID)?", student.getEmail());

                if (!containsName || !containsEmail) {
                    issues.add(new SubmissionIssue("missing student header", IssueSeverity.WARNING, "student name ||| email "
                                    + "was not found near " + "top of " + requiredFile + ".",
                            question.getId(), file.get()));
                }
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                        "unable to read " + requiredFile + ".", question.getId(),
                        questionFolder.resolve(requiredFile)));
            }
        }
        return issues;
    }

    private boolean containsExpectedFieldValue(String header, String label, String expected) {
        return expected != null && !expected.isBlank()
                && Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*"
                        + Pattern.quote(expected.strip()) + "\\h*(?:\\*/)?\\h*$",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE)
                        .matcher(header).find();
    }
}
