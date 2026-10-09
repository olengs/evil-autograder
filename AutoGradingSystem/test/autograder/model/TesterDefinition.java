package autograder.model;

import java.nio.file.Path;

/**
 * TEST-ONLY STUB. Not production code.
 *
 * <p>Minimal stand-in for the teammate-owned {@code TesterDefinition} type.
 * Exposes only the accessors the {@link autograder.workspace.WorkspacePreparer}
 * resolver and the usage examples depend on. The real type is provided by
 * another pair.</p>
 */
public class TesterDefinition {

    private final Path sourcePath;
    private final String mainClass;

    public TesterDefinition(Path sourcePath) {
        this(sourcePath, null);
    }

    public TesterDefinition(Path sourcePath, String mainClass) {
        this.sourcePath = sourcePath;
        this.mainClass = mainClass;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getMainClass() {
        return mainClass;
    }
}
