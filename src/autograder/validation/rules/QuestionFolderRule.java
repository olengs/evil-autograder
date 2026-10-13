package autograder.validation.rules;

import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.ValidationRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestionFolderRule
        implements ValidationRule {

    @Override
    public List<SubmissionIssue> validate(
            StudentSubmission submission,
            QuestionDefinition question) {

        List<SubmissionIssue> issues =
                new ArrayList<>();

        Path expectedFolder =
                submission
                        .getRootDirectory()
                        .resolve(
                            question
                                .getExpectedFolder()
                        );

        if (!Files.isDirectory(
                expectedFolder)) {

            issues.add(
                    new SubmissionIssue(
                            "MISSING_QUESTION_FOLDER",
                            IssueSeverity.ERROR,
                            "Expected folder "
                                    + question
                                        .getExpectedFolder()
                                    + " is missing.",
                            question.getId(),
                            expectedFolder
                    )
            );
        }

        return issues;
    }
}