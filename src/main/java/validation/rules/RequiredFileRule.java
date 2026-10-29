package validation.rules;

import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.AnswerFileLocator;
import validation.ValidationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RequiredFileRule implements ValidationRule {
    private final AnswerFileLocator fileLocator;

    public RequiredFileRule(AnswerFileLocator fileLocator) { //constructor
        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder()); //builds question folder path

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
                    continue;
                }

                issues.add(new SubmissionIssue("missing required file", IssueSeverity.ERROR,
                        "required file " + requiredFile + " is missing.", question.getId(),
                        questionFolder.resolve(requiredFile)));
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                        "unable to inspect " + questionFolder + " > " + e.getMessage(), question.getId(),
                        questionFolder));
            }
        }
        return issues;
    }
}