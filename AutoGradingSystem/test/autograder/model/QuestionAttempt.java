package autograder.model;

import java.nio.file.Path;
import java.util.List;

/**
 * TEST-ONLY STUB. Not production code.
 *
 * <p>Minimal stand-in for the teammate-owned {@code QuestionAttempt} type.
 * Exposes only the accessors the {@link autograder.workspace.WorkspacePreparer}
 * resolvers depend on. The real type is provided by another pair.</p>
 */
public class QuestionAttempt {

    private final String attemptId;
    private final List<Path> answerPaths;

    public QuestionAttempt(String attemptId, List<Path> answerPaths) {
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
