package model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionAttempt {
    private final QuestionDefinition questionDefinition;
    private final Path rootPath;
    private final List<Path> answerFiles;
    private final List<SubmissionIssue> issues;

    public QuestionAttempt(QuestionDefinition questionDefinition, Path rootPath, List<Path> answerFiles, List<SubmissionIssue> issues) {
        this.rootPath = rootPath;
        this.questionDefinition = questionDefinition;
        this.answerFiles = new ArrayList<>(answerFiles);
        this.issues = new ArrayList<>(issues);
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

    public QuestionDefinition getQuestionDefinition() {
        return questionDefinition;
    }

    public List<Path> getAnswerFiles() {
        return Collections.unmodifiableList(answerFiles);
    }

    public List<SubmissionIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean isValid() {
        return !issues.stream().anyMatch(x -> x.getSeverity() == IssueSeverity.ERROR);
    }
}