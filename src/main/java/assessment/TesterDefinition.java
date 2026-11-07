//describes a teacher-provided tester source file & its executable main class
//lets grading copy selected tester into a workspace & run it for matching question

package assessment;

import java.nio.file.Path;

public class TesterDefinition {
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

    public Path getSourceFile() {
        return testerDirectory.resolve(relativePath);
    }

    public String getMainClassName() {
        return mainClassName;
    }
}
