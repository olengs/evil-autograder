package model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionAttempt {
    private final QuestionDefinition questionDefinition;
    private final List<SubmissionIssue> issues;
    private final Path rootPath;
    private final List<Path> answerFiles;

    public QuestionAttempt(QuestionDefinition questionDefinition, Path rootPath, List<Path> answerFiles) {
        this(questionDefinition, rootPath, answerFiles, List.of());
    }

    public QuestionAttempt(QuestionDefinition questionDefinition, Path rootPath,
            List<Path> answerFiles, List<SubmissionIssue> issues) {
        this.questionDefinition = questionDefinition;
        this.rootPath = rootPath;
        this.answerFiles = new ArrayList<>(answerFiles);
        this.issues = List.copyOf(issues);
    }

    public QuestionDefinition getQuestionDefinition() {
        return questionDefinition;
    }

    public List<SubmissionIssue> getIssues() {
        return issues;
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