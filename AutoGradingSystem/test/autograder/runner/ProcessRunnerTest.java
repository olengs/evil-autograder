package autograder.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autograder.model.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ProcessRunner} using the real {@code javac}/{@code java}. */
class ProcessRunnerTest {

    private Path write(Path dir, String name, String content) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    // ---- compile ---------------------------------------------------------

    @Test
    @DisplayName("compile of a valid source succeeds with exit 0 and writes to classes/")
    void compileValid(@TempDir Path ws) throws Exception {
        write(ws, "Main.java", "public class Main { public static void main(String[] a){} }");
        ProcessRunner runner = new ProcessRunner(Duration.ofSeconds(30));
        ProcessResult r = runner.compile(ws);
        assertTrue(r.isSuccessful(), "expected success, stderr=" + r.getErrorOutput());
        assertEquals(0, r.getExitCode());
        assertFalse(r.isTimedOut());
        // .class must land in the workspace's classes/ subfolder, not beside the source
        assertTrue(Files.exists(ws.resolve("classes").resolve("Main.class")),
                "class file should be produced under classes/");
        assertFalse(Files.exists(ws.resolve("Main.class")),
                "class file must NOT be written beside the source");
    }

    @Test
    @DisplayName("compile of a syntax error fails with non-zero exit and stderr")
    void compileSyntaxError(@TempDir Path ws) throws Exception {
        write(ws, "Bad.java", "public class Bad { this is not java }");
        ProcessRunner runner = new ProcessRunner(Duration.ofSeconds(30));
        ProcessResult r = runner.compile(ws);
        assertFalse(r.isSuccessful());
        assertNotEquals(0, r.getExitCode());
        assertFalse(r.getErrorOutput().isBlank(), "javac should report an error");
        assertFalse(r.isTimedOut());
    }

    // ---- runTester -------------------------------------------------------

    @Test
    @DisplayName("runTester captures stdout and exit 0")
    void runCapturesStdout(@TempDir Path ws) throws Exception {
        write(ws, "Main.java",
                "public class Main { public static void main(String[] a){ System.out.println(\"HELLO-123\"); } }");
        ProcessRunner runner = new ProcessRunner(Duration.ofSeconds(30));
        assertTrue(runner.compile(ws).isSuccessful());

        ProcessResult r = runner.runTester(ws, "Main");
        assertTrue(r.isSuccessful());
        assertEquals(0, r.getExitCode());
        assertTrue(r.getStandardOutput().contains("HELLO-123"),
                "stdout was: " + r.getStandardOutput());
        assertFalse(r.isTimedOut());
    }

    @Test
    @DisplayName("runTester reports non-zero exit code from System.exit")
    void runNonZeroExit(@TempDir Path ws) throws Exception {
        write(ws, "Main.java",
                "public class Main { public static void main(String[] a){ System.exit(3); } }");
        ProcessRunner runner = new ProcessRunner(Duration.ofSeconds(30));
        assertTrue(runner.compile(ws).isSuccessful());

        ProcessResult r = runner.runTester(ws, "Main");
        assertEquals(3, r.getExitCode());
        assertFalse(r.isSuccessful());
        assertFalse(r.isTimedOut());
    }

    @Test
    @DisplayName("runTester times out on an infinite loop and is flagged timed-out")
    void runTimeout(@TempDir Path ws) throws Exception {
        write(ws, "Main.java",
                "public class Main { public static void main(String[] a) throws Exception {"
                        + " while(true){ Thread.sleep(10); } } }");
        ProcessRunner runner = new ProcessRunner(Duration.ofMillis(500));
        assertTrue(runner.compile(ws).isSuccessful());

        long started = System.nanoTime();
        ProcessResult r = runner.runTester(ws, "Main");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(r.isTimedOut(), "should be flagged timed-out");
        assertFalse(r.isSuccessful());
        // It must not have hung far beyond the timeout (process was actually killed).
        assertTrue(elapsedMs < 10_000, "runTester should return promptly after timeout; took " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("runTester resolves compiled classes/ and reads resources from the workspace root")
    void runReadsWorkspaceResource(@TempDir Path ws) throws Exception {
        // a resource file placed at the workspace root (as WorkspacePreparer would copy it)
        write(ws, "data.txt", "payload-OK");
        write(ws, "Main.java",
                "import java.nio.file.*;"
                        + "public class Main { public static void main(String[] a) throws Exception {"
                        + " System.out.print(Files.readString(Path.of(\"data.txt\"))); } }");
        ProcessRunner runner = new ProcessRunner(Duration.ofSeconds(30));
        assertTrue(runner.compile(ws).isSuccessful());

        ProcessResult r = runner.runTester(ws, "Main");
        assertTrue(r.isSuccessful(), "stderr=" + r.getErrorOutput());
        assertTrue(r.getStandardOutput().contains("payload-OK"),
                "tester should read the workspace resource; stdout=" + r.getStandardOutput());
    }
}
