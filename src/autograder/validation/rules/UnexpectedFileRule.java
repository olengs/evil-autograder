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

public class UnexpectedFileRule implements SubmissionValidationRule {
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment) {
        List<SubmissionIssue> issues = new ArrayList<>();

        /*
         * build > folder > expected filenames
         * eg. q1 > q1a.java &&& q1b.java
         */

        Map<String, Set<String>> expectedFilesByFolder = buildExpectedFilesMap(assessment);

        for (Map.Entry<String, Set<String>> entry : expectedFilesByFolder.entrySet()) {
            String folderName = entry.getKey();
            Set<String> expectedFiles = entry.getValue();
            Path folder = submission.getRootDirectory().resolve(folderName);

            //missing folders are handled by ~questionfolderrule~

            if (!Files.isDirectory(folder)) {
                continue;
            }

            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    String actualName = file.getFileName().toString();

                    if (expectedFiles.stream().noneMatch(expected -> expected.equalsIgnoreCase(actualName))) {
                        issues.add(new SubmissionIssue("unexpected file", IssueSeverity.WARNING,
                                "unexpected file " + actualName + " found in " + folderName + ".", null,
                                file));
                    }
                });
            } catch (IOException e) {
                for (QuestionDefinition question : assessment.getQuestions()) {
                    if (question.getExpectedFolder().equals(folderName)) {
                        issues.add(new SubmissionIssue("folder read error", IssueSeverity.ERROR,
                                "Unable to inspect folder " + folderName + ": " + e.getMessage(), question.getId(), folder));
                    }
                }
            }
        }
        return issues;
    }

    private Map<String, Set<String>> buildExpectedFilesMap(AssessmentDefinition assessment) {
        Map<String, Set<String>> expectedFilesByFolder = new HashMap<>();

        for (QuestionDefinition question : assessment.getQuestions()) {
            String folder = question.getExpectedFolder();
            expectedFilesByFolder.computeIfAbsent(folder, key -> new HashSet<>()).addAll(question.getRequiredFiles());
            expectedFilesByFolder.get(folder).addAll(question.getAllowedResourceFiles());
        }
        return expectedFilesByFolder;
    }
}
