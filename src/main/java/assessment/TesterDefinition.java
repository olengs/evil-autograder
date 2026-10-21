package assessment;

import java.nio.file.Path;

public class TesterDefinition { //stores a tester's source path & executable main class
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
