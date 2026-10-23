package model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionAttempt {
    private final Path rootPath;
    private final List<Path> answerFiles;

    public QuestionAttempt(QuestionDefinition questionDefinition, Path rootPath, List<Path> answerFiles) {
        this.rootPath = rootPath;
        this.answerFiles = new ArrayList<>(answerFiles);
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