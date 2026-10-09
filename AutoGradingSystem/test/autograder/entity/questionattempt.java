package autograder.entity;

import java.nio.file.Path;
import java.util.List;

/**
 * TEST-ONLY STUB. Not production code.
 *
 * <p>Minimal stand-in for the teammate-owned {@code questionattempt} type.
 * Exposes only the accessors the {@link autograder.workspace.WorkspacePreparer}
 * resolvers depend on.</p>
 */
public class questionattempt {

    private final String attemptId;
    private final List<Path> answerPaths;

    public questionattempt(String attemptId, List<Path> answerPaths) {
        this.attemptId = attemptId;
        this.answerPaths = answerPaths;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public List<Path> getAnswerPaths() {
        return answerPaths;
    }
}
