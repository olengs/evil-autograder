package model;

import java.nio.file.Path;

public class TesterDefinition { //stores where a tester file is & which java class to run
    private final String questionId;
    private final Path testerDirectory;
    private final Path relativePath;
    private final String mainClassName;

    public TesterDefinition(String questionId, Path testerDirectory, Path relativePath, String mainClassName) {
        this.questionId = questionId;
        this.testerDirectory = testerDirectory;
        this.relativePath = relativePath;
        this.mainClassName = mainClassName;
    }

    public String getQuestionId() {
        return questionId;
    }

    public Path getSourceFile() {
        return testerDirectory.resolve(relativePath);
    }

    public String getMainClassName() {
        return mainClassName;
    }
}