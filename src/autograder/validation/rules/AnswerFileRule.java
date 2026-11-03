// Validates question folders, required files, and filename case.
// Returns structural issues without changing student files.

package autograder.validation.rules;

import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.AnswerFileLocator;
import autograder.validation.QuestionValidationRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnswerFileRule implements QuestionValidationRule {
    private final AnswerFileLocator fileLocator;

    public AnswerFileRule(AnswerFileLocator fileLocator) {
        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path folder = submission.getRootDirectory().resolve(question.getExpectedFolder());
        if (!Files.isDirectory(folder)) {
            issues.add(new SubmissionIssue("missing question folder", IssueSeverity.ERROR,
                    "expected folder " + question.getExpectedFolder() + " is missing", question.getId(), folder));
            return issues;
        }

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> candidate = fileLocator.findCandidate(folder, requiredFile);
                if (candidate.isEmpty()) {
                    issues.add(new SubmissionIssue("missing required file", IssueSeverity.ERROR,
                            "required file " + requiredFile + " is missing.", question.getId(),
                            folder.resolve(requiredFile)));
                } else if (!candidate.get().getFileName().toString().equals(requiredFile)) {
                    Path actual = candidate.get();
                    issues.add(new SubmissionIssue("wrong file name", IssueSeverity.ERROR,
                            "expected filename " + requiredFile + " but found " + actual.getFileName() + ".",
                            question.getId(), actual));
                }
            } catch (IOException error) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                        "unable to inspect " + folder + " > " + error.getMessage(), question.getId(), folder));
            }
        }
        return issues;
    }
}
