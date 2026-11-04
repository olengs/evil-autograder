// Checks roster identity fields in the leading comments of required answer files.
// Reports missing and mismatched names or emails without changing submissions.

package validation.rules;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import utils.SubmissionFileFinder;
import utils.StudentHeaderUtils;
import validation.QuestionValidationRule;

public class StudentHeaderRule implements QuestionValidationRule {
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, SubmissionFileFinder files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        StudentDetails student = submission.getStudent();

        if (student == null) {
            issues.add(new SubmissionIssue("unknown student", IssueSeverity.WARNING, "cannot check headers without a matched student", question.getId(), questionFolder));
            return issues;
        }

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> file = files.findAnswerFile(submission.getRootDirectory(), question.getExpectedFolder(), requiredFile);

                if (file.isEmpty()) {
                    continue;
                }

                StudentHeaderUtils.Header header = StudentHeaderUtils.read(file.get());

                checkField(header.name(), "Name", student.getName(), question.getId(), file.get(), issues);
                checkField(header.email(), "Email", student.getEmail(), question.getId(), file.get(), issues);
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to read " + requiredFile + " > " + e.getMessage(), question.getId(), questionFolder.resolve(requiredFile)));
            }
        }
        return issues;
    }

    private void checkField(String found, String field, String expected, String questionId, Path file, List<SubmissionIssue> issues) {
        //@1ejun6 > read actual header value > distinguish missing fields from mismatched values
        boolean missing = found == null || found.isEmpty();
        if (missing || expected == null || !found.equalsIgnoreCase(expected.strip())) {
            String value = found == null ? "(missing)" : found.isEmpty() ? "(blank)" : found;
            issues.add(new SubmissionIssue(missing ? "missing student header" : "mismatched student header",
                    IssueSeverity.WARNING, file.getFileName() + " > " + field + ": expected [" + expected
                    + "] but found [" + value + "]", questionId, file));
        }
    }

}
