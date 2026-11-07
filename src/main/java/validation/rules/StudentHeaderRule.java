//compares names & emails in required answer-file headers with matched student in the student list
//reports missing or mismatched identity fields as warnings without changing submitted source

package validation.rules;

import assessment.QuestionDefinition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import submission.StudentDetails;
import submission.StudentSubmission;
import submission.files.StudentHeaderReader;
import validation.QuestionValidationRule;
import validation.SubmissionIssue;
import validation.ValidationFiles;

public class StudentHeaderRule implements QuestionValidationRule {
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question, ValidationFiles.QuestionFiles files) {
        List<SubmissionIssue> issues = new ArrayList<>();

        StudentDetails student = submission.getStudent();

        if (student == null) {
            issues.add(SubmissionIssue.warning("unknown student", "cannot check headers without a matched student", question.getId()));
            return issues;
        }

        for (String requiredFile : files.requiredAnswers().keySet()) {
            try {
                Optional<Path> file = files.answer(requiredFile).path();

                if (file.isEmpty()) continue;
                StudentHeaderReader.Header header = StudentHeaderReader.read(file.get());

                checkField(header.name(), "Name", student.getName(), question.getId(), file.get(), issues);
                checkField(header.email(), "Email", student.getEmail(), question.getId(), file.get(), issues);
            } catch (IOException error) {
                issues.add(SubmissionIssue.error("file read error", "unable to read " + requiredFile + " > " + error.getMessage(), question.getId()));
            }
        }
        return issues;
    }

    private void checkField(String found, String field, String expected, String questionId, Path file, List<SubmissionIssue> issues) {
        boolean missing = found == null || found.isEmpty();
        if (missing || expected == null || !found.equalsIgnoreCase(expected.strip())) {
            String value = found == null ? "(missing)" : found.isEmpty() ? "(blank)" : found;
            issues.add(SubmissionIssue.warning(missing ? "missing student header" : "mismatched student header", file.getFileName() + " > " + field + ": expected [" + expected + "] but found [" + value + "]", questionId));
        }
    }
}
