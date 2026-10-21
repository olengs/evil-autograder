package autograder.validation.rules;

import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.AnswerFileLocator;
import autograder.validation.ValidationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

//checks filename casing

public class FileNamingRule implements ValidationRule {
    private final AnswerFileLocator fileLocator;

    public FileNamingRule(AnswerFileLocator fileLocator) { //constructor
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

                Optional<Path> ignoringCase = fileLocator.findIgnoringCase(questionFolder, requiredFile);
                if (ignoringCase.isPresent()) {
                    Path actualFile = ignoringCase.get();
                    issues.add(new SubmissionIssue("wrong file name", IssueSeverity.ERROR,
                            "expected filename " + requiredFile + " but found " + actualFile.getFileName().toString() + ".",
                            question.getId(), actualFile));
                }
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                        "unable to inspect " + questionFolder + ".", question.getId(), questionFolder));
            }
        }
        return issues;
    }
}