package validation.rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.SubmissionFileFinder;
import validation.ValidationRule;
import utils.StudentHeaderReader;

public class StudentHeaderRule implements ValidationRule { //checks student name & email in comments at the top of each required answer
    private final SubmissionFileFinder fileLocator;

    public StudentHeaderRule(SubmissionFileFinder fileLocator) {
        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        if (!Files.isDirectory(questionFolder)) {
            return issues;
        }

        StudentDetails student = submission.getStudent();

        if (student == null) {
            issues.add(new SubmissionIssue("unknown student", IssueSeverity.WARNING, "cannot check headers without a matched student", question.getId(), questionFolder));
            return issues;
        }

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> file = fileLocator.findExact(questionFolder, requiredFile);
                if (file.isEmpty()) file = fileLocator.findIgnoringCase(questionFolder, requiredFile);

                if (file.isEmpty()) {
                    continue;
                }

                var header = StudentHeaderReader.read(file.get());

                checkField(header.names(), "Name", student.getName(), question.getId(), file.get(), issues);
                checkField(header.emails(), "Email", student.getEmail(), question.getId(), file.get(), issues);
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to read " + requiredFile + " > " + e.getMessage(), question.getId(), questionFolder.resolve(requiredFile)));
            }
        }
        return issues;
    }

    private void checkField(List<String> found, String field, String expected,
            String questionId, Path file, List<SubmissionIssue> issues) {
        List<String> populated = found.stream().filter(value -> !value.isBlank()).toList();
        boolean missing = populated.isEmpty();
        boolean mismatched = expected == null || populated.stream()
                .anyMatch(value -> !value.equalsIgnoreCase(expected.strip()));
        if (missing || mismatched) {
            String value = found.isEmpty() ? "(missing)" : missing ? "(blank)" : String.join("; ", populated);
            issues.add(new SubmissionIssue(missing ? "missing student header" : "mismatched student header",
                    IssueSeverity.WARNING, file.getFileName() + " > " + field + ": expected [" + expected
                    + "] but found [" + value + "]", questionId, file));
        }
    }
}
