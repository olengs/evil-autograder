package validation.rules;

import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.SubmissionValidationRule;

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

public class UnexpectedFileRule implements SubmissionValidationRule { //warns about extra files across all question folders in a submission
    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment) {
        List<SubmissionIssue> issues = new ArrayList<>();

        Map<String, Set<String>> expectedFilesByFolder = buildExpectedFilesMap(assessment);

        for (Map.Entry<String, Set<String>> entry : expectedFilesByFolder.entrySet()) {
            String questionFolderName = entry.getKey();
            Set<String> expectedFiles = entry.getValue();
            Path questionDirectory = submission.getRootDirectory().resolve(questionFolderName);

            if (!Files.isDirectory(questionDirectory)) {
                continue;
            }

            try (Stream<Path> files = Files.list(questionDirectory)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    String actualName = file.getFileName().toString();

                    if (expectedFiles.stream().noneMatch(expected -> expected.equalsIgnoreCase(actualName))) {
                        issues.add(new SubmissionIssue("unexpected file", IssueSeverity.WARNING,
                                "unexpected file " + actualName + " found in " + questionFolderName + ".", null,
                                file));
                    }
                });
            } catch (IOException error) {
                issues.add(new SubmissionIssue("folder read error", IssueSeverity.ERROR,
                        "Unable to inspect folder " + questionFolderName + ": " + error.getMessage(), null, questionDirectory));
            }
        }
        return issues;
    }

    private Map<String, Set<String>> buildExpectedFilesMap(AssessmentDefinition assessment) {
        Map<String, Set<String>> expectedFilesByFolder = new HashMap<>();

        for (QuestionDefinition question : assessment.getQuestions()) {
            String questionDirectory = question.getExpectedFolder();
            expectedFilesByFolder.computeIfAbsent(questionDirectory, key -> new HashSet<>()).addAll(question.getRequiredFiles());
            expectedFilesByFolder.get(questionDirectory).addAll(question.getAllowedResourceFiles());
        }
        return expectedFilesByFolder;
    }
}