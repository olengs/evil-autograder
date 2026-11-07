//describes a teacher-provided tester source file & its executable main class
//lets grading copy selected tester into a workspace & run it for matching question

package assessment;

import java.nio.file.Path;

public class TesterDefinition {
    private final Path testerDirectory;
    private final Path relativePath;
    private final String mainClassName;

    public TesterDefinition(Path testerDirectory, Path relativePath, String mainClassName) {
        this.testerDirectory = testerDirectory;
        this.relativePath = relativePath;
        this.mainClassName = mainClassName;
    }

    public Path getSourceFile() {
        return testerDirectory.resolve(relativePath);
    }

    public String getMainClassName() {
        return mainClassName;
    }
}
