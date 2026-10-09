package autograder.entity;

import java.nio.file.Path;

/**
 * TEST-ONLY STUB. Not production code.
 *
 * <p>Minimal stand-in for the teammate-owned {@code questiondefinition} type so
 * the workspace-preparation stage can be compiled and tested in isolation. The
 * real type is provided by another pair; this stub only exposes the getters the
 * {@link autograder.workspace.WorkspacePreparer} resolvers depend on.</p>
 */
public class questiondefinition {

    private final Path questionFolder;
    private final testerdefinition tester;

    public questiondefinition(Path questionFolder, testerdefinition tester) {
        this.questionFolder = questionFolder;
        this.tester = tester;
    }

    public Path getQuestionFolder() {
        return questionFolder;
    }

    public testerdefinition getTester() {
        return tester;
    }
}
