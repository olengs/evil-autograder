package model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionAttempt {
    private final Path rootPath;
    private final List<Path> answerFiles;
    private final QuestionDefinition questionDefinition;
    private final List<SubmissionIssue> issues;

    public QuestionAttempt(QuestionDefinition questionDefinition, Path rootPath, List<Path> answerFiles) {
        this(questionDefinition, rootPath, answerFiles, List.of());
    }

    // Validation supplies full answer paths; grading stores paths relative to the attempt root.
    public QuestionAttempt(QuestionDefinition questionDefinition, List<Path> answerFiles, List<SubmissionIssue> issues) {
        this(questionDefinition,
            answerFiles.isEmpty() ? questionDefinition.getFolderPath()
                : answerFiles.get(0).toAbsolutePath().normalize().getParent(),
            answerFiles.stream().map(Path::getFileName).toList(), issues);
    }

    private QuestionAttempt(QuestionDefinition questionDefinition, Path rootPath,
                            List<Path> answerFiles, List<SubmissionIssue> issues) {
        this.rootPath = rootPath;
        this.answerFiles = new ArrayList<>(answerFiles);
        this.questionDefinition = questionDefinition;
        this.issues = List.copyOf(issues);
    }

    public QuestionDefinition getQuestionDefinition() {
        return questionDefinition;
    }

    public boolean isValid() {
        return issues.stream().noneMatch(issue -> issue.getSeverity() == IssueSeverity.ERROR);
    }

    public Path getAttempRootPath() {
        return rootPath;
    }

    public List<Path> getAttemptRelativePaths() {
        return answerFiles;
    }

    public List<Path> getAttemptFullPaths() {
        return answerFiles.stream().map(x -> rootPath.resolve(x)).toList();
    }

    public List<Path> getAnswerFiles() {
        return Collections.unmodifiableList(answerFiles);
    }
}
