package validation.rules;

import assessment.AssessmentDefinition;
import assessment.QuestionDefinition;
import utils.files.FileUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import submission.StudentSubmission;
import validation.SubmissionIssue;
import validation.SubmissionValidationRule;
import validation.ValidationFiles;

public class UnexpectedFileRule implements SubmissionValidationRule { //warns about unexpected files in question folders
    private final Map<AssessmentDefinition, Map<String, Set<String>>> expectedFilesCache = new HashMap<>();

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, AssessmentDefinition assessment, ValidationFiles validationFiles) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Map<String, Set<String>> expectedFilesByFolder = expectedFilesCache.computeIfAbsent(assessment, this::buildExpectedFilesMap);

        for (Map.Entry<String, Set<String>> entry : expectedFilesByFolder.entrySet()) {
            String questionFolderName = entry.getKey();
            Set<String> expectedFiles = entry.getValue();
            ValidationFiles.Lookup folder = validationFiles.folder(questionFolderName);
            //question rules report failed folder lookups > missing folders have no files to inspect
            if (folder.path().isEmpty()) continue;
            Path questionDirectory = folder.path().get();

            try (Stream<Path> files = FileUtils.streamRegularFiles(questionDirectory)) {
                files.forEach(file -> {
                    String actualName = file.getFileName().toString();

                    if (!expectedFiles.contains(actualName)) {
                        issues.add(SubmissionIssue.warning("unexpected file", "unexpected file " + actualName + " found in " + questionFolderName + ".", null));
                    }
                });
            } catch (IOException | UncheckedIOException error) {
                issues.add(SubmissionIssue.error("folder read error", "Unable to inspect folder " + questionFolderName + ": " + error.getMessage(), null));
            }
        }
        return issues;
    }

    private Map<String, Set<String>> buildExpectedFilesMap(AssessmentDefinition assessment) {
        Map<String, Set<String>> expectedFilesByFolder = new LinkedHashMap<>();

        for (QuestionDefinition question : assessment.getQuestions()) {
            String questionDirectory = question.getExpectedFolderName();
            expectedFilesByFolder.computeIfAbsent(questionDirectory, key -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).addAll(question.getRequiredAnswerFiles());
            expectedFilesByFolder.get(questionDirectory).addAll(question.getSupportFiles());
        }
        expectedFilesByFolder.replaceAll((folder, names) -> Collections.unmodifiableSet(names));
        return Collections.unmodifiableMap(expectedFilesByFolder);
    }
}
