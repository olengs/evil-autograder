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

public class RequiredFileRule
        implements ValidationRule {

    private final AnswerFileLocator fileLocator;

    public RequiredFileRule(
            AnswerFileLocator fileLocator) {

        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(
            StudentSubmission submission,
            QuestionDefinition question) {

        List<SubmissionIssue> issues =
                new ArrayList<>();

        Path questionFolder =
                submission
                        .getRootDirectory()
                        .resolve(
                            question
                                .getExpectedFolder()
                        );

        /*
         * Folder rule already handles this.
         *
         * Don't create lots of redundant
         * "missing file" errors when the
         * entire folder is missing.
         */
        if (!Files.isDirectory(
                questionFolder)) {

            return issues;
        }

        for (String requiredFile :
                question.getRequiredFiles()) {

            try {

                Optional<Path> exact =
                        fileLocator.findExact(
                                questionFolder,
                                requiredFile);

                if (exact.isPresent()) {
                    continue;
                }

                /*
                 * Check whether a similar
                 * case-only filename exists.
                 *
                 * FileNamingRule will report
                 * this separately.
                 */
                Optional<Path> ignoringCase =
                        fileLocator
                            .findIgnoringCase(
                                questionFolder,
                                requiredFile);

                if (ignoringCase.isPresent()) {
                    continue;
                }

                issues.add(
                        new SubmissionIssue(
                                "MISSING_REQUIRED_FILE",
                                IssueSeverity.ERROR,
                                "Required file "
                                        + requiredFile
                                        + " is missing.",
                                question.getId(),
                                questionFolder
                                    .resolve(
                                        requiredFile)
                        )
                );

            } catch (IOException e) {

                issues.add(
                        new SubmissionIssue(
                                "FILE_READ_ERROR",
                                IssueSeverity.ERROR,
                                "Unable to inspect "
                                        + questionFolder
                                        + ": "
                                        + e.getMessage(),
                                question.getId(),
                                questionFolder
                        )
                );
            }
        }

        return issues;
    }
}