package autograder.validation.rules;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionDefinition;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.validation.SubmissionValidationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class UnexpectedFileRule
        implements SubmissionValidationRule {

    @Override
    public List<SubmissionIssue> validate(
            StudentSubmission submission,
            AssessmentDefinition assessment) {

        List<SubmissionIssue> issues =
                new ArrayList<>();

        /*
         * Build:
         *
         * folder -> expected filenames
         *
         * Example:
         *
         * Q1 -> Q1a.java, Q1b.java
         * Q2 -> Q2.java
         * Q3 -> Q3.java
         */
        Map<String, Set<String>>
                expectedFilesByFolder =
                buildExpectedFilesMap(
                        assessment);

        for (Map.Entry<String, Set<String>>
                entry :
                expectedFilesByFolder.entrySet()) {

            String folderName =
                    entry.getKey();

            Set<String> expectedFiles =
                    entry.getValue();

            Path folder =
                    submission
                            .getRootDirectory()
                            .resolve(folderName);

            /*
             * Missing folders are handled
             * by QuestionFolderRule.
             */
            if (!Files.isDirectory(folder)) {
                continue;
            }

            try (Stream<Path> files =
                         Files.list(folder)) {

                files
                    .filter(Files::isRegularFile)
                    .forEach(file -> {

                        String actualName =
                                file.getFileName()
                                        .toString();

                        if (!expectedFiles
                                .contains(actualName)) {

                            issues.add(
                                    new SubmissionIssue(
                                            "UNEXPECTED_FILE",
                                            IssueSeverity.WARNING,
                                            "Unexpected file "
                                                    + actualName
                                                    + " found in "
                                                    + folderName
                                                    + ".",
                                            null,
                                            file
                                    )
                            );
                        }
                    });

            } catch (IOException e) {

                issues.add(
                        new SubmissionIssue(
                                "FOLDER_READ_ERROR",
                                IssueSeverity.ERROR,
                                "Unable to inspect folder "
                                        + folderName
                                        + ": "
                                        + e.getMessage(),
                                null,
                                folder
                        )
                );
            }
        }

        return issues;
    }

    private Map<String, Set<String>>
            buildExpectedFilesMap(
                    AssessmentDefinition assessment) {

        Map<String, Set<String>>
                expectedFilesByFolder =
                new HashMap<>();

        for (QuestionDefinition question :
                assessment.getQuestions()) {

            String folder =
                    question.getExpectedFolder();

            expectedFilesByFolder
                    .computeIfAbsent(
                            folder,
                            key -> new HashSet<>())
                    .addAll(
                            question.getRequiredFiles());
        }

        return expectedFilesByFolder;
    }
}