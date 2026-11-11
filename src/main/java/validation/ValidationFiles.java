package validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import assessment.QuestionDefinition;
import submission.StudentSubmission;
import submission.files.SubmissionFileFinder;

/// shares cached question-folder & answer-file lookups across validation
public final class ValidationFiles {
    private final Path root;
    private final SubmissionFileFinder finder;
    private final Map<String, Lookup> folders = new HashMap<>();
    private final Map<AnswerKey, Lookup> answers = new HashMap<>();
    private final Map<QuestionDefinition, QuestionFiles> questions = new HashMap<>();

    private record AnswerKey(String folderName, String filename) {}

    public record Lookup(Optional<Path> path, IOException error) {}

    public ValidationFiles(StudentSubmission submission, SubmissionFileFinder finder) {
        root = submission.getRootDirectory();
        this.finder = finder.forSubmission();
    }

    public Lookup folder(String folderName) {
        return folders.computeIfAbsent(folderName, name -> {
            try {
                return new Lookup(finder.findQuestionFolder(root, name), null);
            } catch (IOException error) {
                return new Lookup(Optional.empty(), error);
            }
        });
    }

    public QuestionFiles question(QuestionDefinition question) {
        return questions.computeIfAbsent(question, QuestionFiles::new);
    }

    public final class QuestionFiles {
        private final String folderName;
        private final Path expectedFolder;
        private final Lookup actualFolder;
        private final Map<String, Lookup> requiredAnswers = new LinkedHashMap<>();

        private QuestionFiles(QuestionDefinition question) {
            folderName = question.getExpectedFolderName();
            expectedFolder = root.resolve(folderName);
            actualFolder = folder(folderName);
            for (String filename : question.getRequiredAnswerFiles()) requiredAnswers.put(filename, answer(filename));
        }

        public Path expectedFolder() {
            return expectedFolder;
        }

        public Lookup actualFolder() {
            return actualFolder;
        }

        public Map<String, Lookup> requiredAnswers() {
            return Collections.unmodifiableMap(requiredAnswers);
        }

        public Lookup answer(String filename) {
            //a failed folder lookup already explains why its answers cannot be resolved
            if (actualFolder.error() != null) return actualFolder;
            return answers.computeIfAbsent(new AnswerKey(folderName, filename), key -> {
                try {
                    return new Lookup(finder.findAnswerFile(root, actualFolder.path(), key.filename()), null);
                } catch (IOException error) {
                    return new Lookup(Optional.empty(), error);
                }
            });
        }
    }
}
