package validation.rules;

import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.SubmissionFileFinder;
import validation.ValidationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileNamingRule implements ValidationRule { //checks that answer filenames use expected capital letters
    private final SubmissionFileFinder fileLocator;

    public FileNamingRule(SubmissionFileFinder fileLocator) { //@1ejun6 > constructor
        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        if (!Files.isDirectory(questionFolder)) {
            return issues;
        }

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> exact = fileLocator.findExact(questionFolder, requiredFile);
                if (exact.isPresent()) {
                    continue;
                }

                Optional<Path> caseInsensitiveMatch = fileLocator.findIgnoringCase(questionFolder, requiredFile);
                if (caseInsensitiveMatch.isPresent()) {
                    Path actualFile = caseInsensitiveMatch.get();
                    issues.add(new SubmissionIssue("wrong file name", IssueSeverity.ERROR,
                            "expected filename " + requiredFile + " but found " + actualFile.getFileName().toString() + ".",
                            question.getId(), actualFile));
                }
            } catch (IOException error) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to inspect " + questionFolder + " > " + error.getMessage(), question.getId(), questionFolder));
            }
        }
        return issues;
    }
}