package autograder.workspace;

import autograder.autograderexception.WorkspacePreparationException;
import autograder.entity.questionattempt;
import autograder.entity.questiondefinition;
import autograder.entity.testerdefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * TEST-ONLY helper. Not production code.
 *
 * <p>{@code Files.createTempDirectory} resolves {@code java.io.tmpdir} once at
 * JVM startup, so a temp-dir creation failure can only be exercised in a child
 * JVM launched with {@code -Djava.io.tmpdir=<a regular file>}. This main calls
 * {@link WorkspacePreparer#prepare} and prints the resulting error code so the
 * parent test can assert {@code wsp-001}.</p>
 */
public final class Wsp001Probe {

    private Wsp001Probe() {
    }

    public static void main(String[] args) {
        WorkspacePreparer prep = new WorkspacePreparer();
        try {
            prep.prepare(
                    new questiondefinition(Path.of("qres"),
                            new testerdefinition(Path.of("Tester.java"))),
                    new questionattempt("a1", List.of()));
            System.out.println("CODE=none");
        } catch (WorkspacePreparationException e) {
            System.out.println("CODE=" + e.getCode());
        }
    }
}
