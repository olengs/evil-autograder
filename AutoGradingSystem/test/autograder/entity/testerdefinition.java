package autograder.entity;

import java.nio.file.Path;

/**
 * TEST-ONLY STUB. Not production code.
 *
 * <p>Minimal stand-in for the teammate-owned {@code testerdefinition} type.
 * Exposes only the accessor the {@link autograder.workspace.WorkspacePreparer}
 * resolver depends on.</p>
 */
public class testerdefinition {

    private final Path sourcePath;

    public testerdefinition(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Path getSourcePath() {
        return sourcePath;
    }
}
