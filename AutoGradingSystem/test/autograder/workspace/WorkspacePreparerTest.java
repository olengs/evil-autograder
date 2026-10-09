package autograder.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autograder.autograderException.WorkspacePreparationException;
import autograder.entity.questionattempt;
import autograder.entity.questiondefinition;
import autograder.entity.testerdefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link WorkspacePreparer}. */
class WorkspacePreparerTest {

    // ---- helpers ---------------------------------------------------------

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    private questiondefinition question(Path folder, Path testerSource) {
        return new questiondefinition(folder, new testerdefinition(testerSource));
    }

    private questionattempt attempt(String id, Path... answers) {
        return new questionattempt(id, List.of(answers));
    }

    // ---- prepare: basic contract ----------------------------------------

    @Test
    @DisplayName("prepare returns an existing, writable directory")
    void prepareReturnsExistingWritableDir(@TempDir Path tmp) throws Exception {
        Path qFolder = tmp.resolve("qres");
        writeFile(qFolder, "resource.txt", "r");
        Path tester = writeFile(tmp, "Tester.java", "class Tester {}");
        Path answer = writeFile(tmp, "Answer.java", "class Answer {}");

        try (WorkspacePreparer prep = new WorkspacePreparer()) {
            Path ws = prep.prepare(question(qFolder, tester), attempt("a1", answer));
            assertTrue(Files.exists(ws), "workspace should exist");
            assertTrue(Files.isDirectory(ws), "workspace should be a directory");
            assertTrue(Files.isWritable(ws), "workspace should be writable");
        }
    }

    @Test
    @DisplayName("two prepare calls produce distinct workspaces")
    void twoCallsDistinct(@TempDir Path tmp) throws Exception {
        Path qFolder = tmp.resolve("qres");
        writeFile(qFolder, "r.txt", "r");
        Path tester = writeFile(tmp, "Tester.java", "class Tester {}");
        Path answer = writeFile(tmp, "Answer.java", "class Answer {}");
        questiondefinition q = question(qFolder, tester);

        WorkspacePreparer p1 = new WorkspacePreparer();
        WorkspacePreparer p2 = new WorkspacePreparer();
        Path w1 = p1.prepare(q, attempt("a1", answer));
        Path w2 = p2.prepare(q, attempt("a2", answer));
        try {
            assertNotEquals(w1, w2, "distinct workspaces expected");
            assertTrue(Files.exists(w1));
            assertTrue(Files.exists(w2));
        } finally {
            p1.close();
            p2.close();
        }
    }

    // ---- wsp-001: creation failure --------------------------------------

    @Test
    @DisplayName("wsp-001 when temp workspace cannot be created")
    void wsp001OnCreationFailure(@TempDir Path tmp) throws Exception {
        // Files.createTempDirectory resolves java.io.tmpdir once at JVM
        // startup, so the creation-failure path can only be exercised in a
        // child JVM whose tmpdir points at a regular file (not a directory).
        Path notADir = writeFile(tmp, "not-a-dir", "x");

        String javaBin = System.getProperty("java.home")
                + java.io.File.separator + "bin" + java.io.File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        Process proc = new ProcessBuilder(
                javaBin,
                "-Djava.io.tmpdir=" + notADir,
                "-cp", classpath,
                "autograder.workspace.Wsp001Probe")
                .redirectErrorStream(true)
                .start();

        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        proc.waitFor();

        assertTrue(output.contains("CODE=wsp-001"),
                "child JVM should report wsp-001; actual output:\n" + output);
    }

    // ---- cleanup / close -------------------------------------------------

    @Test
    @DisplayName("cleanup removes a populated tree and is idempotent")
    void cleanupRemovesPopulatedTree(@TempDir Path tmp) throws Exception {
        Path tree = tmp.resolve("tree");
        writeFile(tree.resolve("sub"), "a.txt", "a");
        writeFile(tree, "b.txt", "b");

        WorkspacePreparer prep = new WorkspacePreparer();
        prep.cleanup(tree);
        assertFalse(Files.exists(tree), "tree should be gone");
        // idempotent: second call is a no-op
        prep.cleanup(tree);
        // null is also a no-op
        prep.cleanup(null);
    }

    @Test
    @DisplayName("close() and try-with-resources auto-clean the workspace")
    void closeAutoCleans(@TempDir Path tmp) throws Exception {
        Path qFolder = tmp.resolve("qres");
        writeFile(qFolder, "r.txt", "r");
        Path tester = writeFile(tmp, "Tester.java", "class Tester {}");
        Path answer = writeFile(tmp, "Answer.java", "class Answer {}");

        Path captured;
        try (WorkspacePreparer prep = new WorkspacePreparer()) {
            captured = prep.prepare(question(qFolder, tester), attempt("a1", answer));
            assertTrue(Files.exists(captured));
        }
        assertFalse(Files.exists(captured), "try-with-resources should auto-clean");

        // close() with nothing prepared is safe
        new WorkspacePreparer().close();
    }

    // ---- self-clean on failure ------------------------------------------

    @Test
    @DisplayName("self-clean leaves nothing behind when preparation fails")
    void selfCleanOnFailure(@TempDir Path tmp) throws Exception {
        // Missing question folder triggers wsp-003 after temp dir creation.
        Path tester = writeFile(tmp, "Tester.java", "class Tester {}");
        Path answer = writeFile(tmp, "Answer.java", "class Answer {}");
        Path missingFolder = tmp.resolve("does-not-exist");

        WorkspacePreparer prep = new WorkspacePreparer();
        long before = countTempWorkspaces();
        WorkspacePreparationException ex = assertThrows(
                WorkspacePreparationException.class,
                () -> prep.prepare(question(missingFolder, tester), attempt("a1", answer)));
        assertEquals("wsp-003", ex.getCode());
        long after = countTempWorkspaces();
        assertEquals(before, after, "failed prepare must leave no temp workspace behind");
    }

    private long countTempWorkspaces() throws IOException {
        Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir"));
        try (var s = Files.list(tmpRoot)) {
            return s.filter(p -> p.getFileName().toString().startsWith("autograder-ws-")).count();
        }
    }

    // ---- per-input copy semantics ---------------------------------------

    @Test
    @DisplayName("answer files are copied into the workspace (wsp-002 path)")
    void answerFilesCopied(@TempDir Path tmp) throws Exception {
        Path a1 = writeFile(tmp, "A1.java", "class A1 {}");
        Path a2 = writeFile(tmp, "A2.java", "class A2 {}");
        Path target = tmp.resolve("ws");
        Files.createDirectories(target);

        WorkspacePreparer prep = new WorkspacePreparer();
        prep.copyFile(attempt("a1", a1, a2), target);
        assertTrue(Files.exists(target.resolve("A1.java")));
        assertTrue(Files.exists(target.resolve("A2.java")));
        assertEquals("class A1 {}", Files.readString(target.resolve("A1.java")));
        // source untouched
        assertTrue(Files.exists(a1));
    }

    @Test
    @DisplayName("answer copy failure yields wsp-002")
    void answerCopyFailureWsp002(@TempDir Path tmp) throws Exception {
        Path missing = tmp.resolve("missing").resolve("Nope.java");
        Path target = tmp.resolve("ws");
        Files.createDirectories(target);

        WorkspacePreparer prep = new WorkspacePreparer();
        WorkspacePreparationException ex = assertThrows(
                WorkspacePreparationException.class,
                () -> prep.copyFile(attempt("a1", missing), target));
        assertEquals("wsp-002", ex.getCode());
    }

    @Test
    @DisplayName("question resource folder is copied recursively")
    void resourceFolderCopied(@TempDir Path tmp) throws Exception {
        Path qFolder = tmp.resolve("qres");
        writeFile(qFolder, "top.txt", "top");
        writeFile(qFolder.resolve("sub"), "nested.txt", "nested");
        Path target = tmp.resolve("ws");
        Files.createDirectories(target);

        WorkspacePreparer prep = new WorkspacePreparer();
        prep.copyFile(question(qFolder, tmp.resolve("Tester.java")), target);
        assertTrue(Files.exists(target.resolve("top.txt")));
        assertTrue(Files.exists(target.resolve("sub").resolve("nested.txt")));
        assertEquals("nested", Files.readString(target.resolve("sub").resolve("nested.txt")));
    }

    @Test
    @DisplayName("missing question resource folder yields wsp-003")
    void missingResourceFolderWsp003(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("ws");
        Files.createDirectories(target);
        WorkspacePreparer prep = new WorkspacePreparer();
        WorkspacePreparationException ex = assertThrows(
                WorkspacePreparationException.class,
                () -> prep.copyFile(question(tmp.resolve("nope"), tmp.resolve("T.java")), target));
        assertEquals("wsp-003", ex.getCode());
    }

    @Test
    @DisplayName("tester source is copied (wsp-004 path)")
    void testerCopied(@TempDir Path tmp) throws Exception {
        Path tester = writeFile(tmp, "Tester.java", "class Tester {}");
        Path target = tmp.resolve("ws");
        Files.createDirectories(target);

        WorkspacePreparer prep = new WorkspacePreparer();
        prep.copyFile(new testerdefinition(tester), target);
        assertTrue(Files.exists(target.resolve("Tester.java")));
        assertEquals("class Tester {}", Files.readString(target.resolve("Tester.java")));
    }

    @Test
    @DisplayName("missing tester source yields wsp-004")
    void missingTesterWsp004(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("ws");
        Files.createDirectories(target);
        WorkspacePreparer prep = new WorkspacePreparer();
        WorkspacePreparationException ex = assertThrows(
                WorkspacePreparationException.class,
                () -> prep.copyFile(new testerdefinition(tmp.resolve("Nope.java")), target));
        assertEquals("wsp-004", ex.getCode());
    }

    // ---- end-to-end ------------------------------------------------------

    @Test
    @DisplayName("end-to-end prepare assembles answer + resources + tester")
    void endToEndAssembly(@TempDir Path tmp) throws Exception {
        Path qFolder = tmp.resolve("qres");
        writeFile(qFolder, "spec.txt", "spec");
        writeFile(qFolder.resolve("data"), "input.csv", "1,2,3");
        Path tester = writeFile(tmp, "Tester.java", "class Tester {}");
        Path answer = writeFile(tmp, "Solution.java", "class Solution {}");

        try (WorkspacePreparer prep = new WorkspacePreparer()) {
            Path ws = prep.prepare(question(qFolder, tester), attempt("student-42", answer));
            assertTrue(Files.exists(ws.resolve("Solution.java")), "answer present");
            assertTrue(Files.exists(ws.resolve("spec.txt")), "resource present");
            assertTrue(Files.exists(ws.resolve("data").resolve("input.csv")), "nested resource present");
            assertTrue(Files.exists(ws.resolve("Tester.java")), "tester present");
        }
    }
}
