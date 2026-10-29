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

    public Path getRelativePath() {
        return relativePath;
    }

    public Path getSourceFile() { //@1ejun6 > resolve the source file under tester directory
        return testerDirectory.resolve(relativePath);
    }

    public String getMainClassName() { //@1ejun6 > return exact java class name to run
        return mainClassName;
    }
}