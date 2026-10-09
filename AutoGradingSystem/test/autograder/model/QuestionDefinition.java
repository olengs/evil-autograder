package autograder.model;

import java.nio.file.Path;

/**
 * TEST-ONLY STUB. Not production code.
 *
 * <p>Minimal stand-in for the teammate-owned {@code QuestionDefinition} type so
 * the workspace-preparation stage can be compiled and tested in isolation. The
 * real type is provided by another pair; this stub only exposes the getters the
 * {@link autograder.workspace.WorkspacePreparer} resolvers depend on.</p>
 */
public class QuestionDefinition {

    private final Path questionFolder;
    private final TesterDefinition tester;

    public QuestionDefinition(Path questionFolder, TesterDefinition tester) {
        this.questionFolder = questionFolder;
        this.tester = tester;
    }

    public Path getQuestionFolder() {
        return questionFolder;
    }

    public TesterDefinition getTester() {
        return tester;
    }
}
