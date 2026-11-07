//checks ui integration with normalizer grading & reporting using temporary assessment fixtures
//covers progress & source normalization & failures & setup recovery & terminal rendering

package grading.verification;

import static org.junit.jupiter.api.Assertions.*;

import com.opencsv.CSVReader;
import config.Config;
import config.SetupConfig;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import grading.GradeStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import grading.progress.GradingProgress;
import grading.progress.ProgressStage;
import report.ScoreSheetColumns;
import ui.GradingView;
import ui.panel.StudentPanel;

class NormalizerUiIntegrationTest {
    @TempDir Path directory;

    @Test
    void uiGradesNormalizedAnswersAndSavesAllOutcomes() throws Exception {
        Path configFile = fixture();
        archive("good", "return 42;", "q1/q1.java");
        archive("compile", "return doesNotExist;", "Q1/Q1.java");
        archive("runtime", "throw new IllegalStateException();", "Q1/Q1.java");
        archive("timeout", "while (true) {}", "Q1/Q1.java");
        archive("missing", null, "notes.txt");
        var setup = new SetupConfig(configFile);
        assertNull(setup.issue(), "a missing scoresheet must be allowed so normalizer can generate it");
        Config config = setup.config();
        var progress = new RecordingProgress();

        var checks = JUnitRunner.run(config, progress);

        assertEquals(Map.of("good", GradeStatus.completed, "compile", GradeStatus.compileError,
                "runtime", GradeStatus.runtimeError, "timeout", GradeStatus.timeout,
                "missing", GradeStatus.skipped), progress.outcomes);
        assertEquals(4, progress.compilations);
        assertEquals(3, progress.executions);
        assertEquals(6, checks.getTestsSucceededCount());
        assertEquals(0, checks.getTotalFailureCount());
        var summary = progress.getSnapshot();
        assertEquals(ProgressStage.completed, summary.stage());
        assertEquals(5, summary.processedStudents());
        assertEquals(5, summary.processedQuestions());
        assertEquals(progress.firstEstimate, summary.estimatedTotal().orElseThrow());
        assertEquals(summary.totalElapsed(), progress.getSnapshot().totalElapsed());

        Path output = config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName());
        Map<String, String> marks = new LinkedHashMap<>();
        try (var reader = new CSVReader(Files.newBufferedReader(output))) {
            List<String[]> rows = reader.readAll();
            for (String[] row : rows.subList(1, rows.size())) {
                marks.put(row[ScoreSheetColumns.username], row[ScoreSheetColumns.gradeNumerator]);
            }
        }
        assertEquals(Map.of("good", "1", "compile", "0", "runtime", "0", "timeout", "0", "missing", "0"), marks);
        try (var zip = new java.util.zip.ZipFile(directory.resolve("submissions/good.zip").toFile())) {
            assertNotNull(zip.getEntry("q1/q1.java"), "normalization must leave original archive unchanged");
        }
        for (int[] size : List.of(new int[]{96, 34}, new int[]{79, 22}, new int[]{60, 25}, new int[]{32, 25})) {
            Buffer buffer = Buffer.empty(new Rect(0, 0, size[0], size[1]));
            new GradingView(config.getInputDirectory(), summary).render(Frame.forTesting(buffer));
            String screen = text(buffer);
            assertTrue(screen.contains("grading complete"));
            assertTrue(screen.contains("actual duration"));
            assertTrue(screen.contains("aborted"), "summary counts must fit at " + size[0] + "x" + size[1]);
        }
    }

    @Test
    void replacementPathsOverrideConfigWithoutRewritingIt() throws Exception {
        Path configFile = fixture();
        String original = Files.readString(configFile);
        Files.delete(directory.resolve("testers/Q1Tester.java"));
        Files.delete(directory.resolve("testers"));
        var setup = new SetupConfig(configFile);
        assertEquals("testers.folder", setup.issue().setting());
        Path replacement = Files.createDirectory(directory.resolve("replacement-testers"));

        setup.replace(replacement.toString());

        assertNull(setup.issue());
        assertEquals(replacement, setup.config().getTestersDirectory());
        assertEquals(original, Files.readString(configFile));
    }

    @Test
    void cancellationStopsGradingAndFreezesFailedSummary() throws Exception {
        Config config = new Config(fixture());
        archive("cancel", "while (true) {}", "Q1/Q1.java");
        var progress = new GradingProgress() {
            @Override
            public synchronized void executionStarted(String question, Duration timeout) {
                super.executionStarted(question, timeout);
                Thread.currentThread().interrupt();
            }
        };
        try {
            assertThrows(RuntimeException.class, () -> JUnitRunner.run(config, progress));
            assertTrue(Thread.currentThread().isInterrupted());
            var summary = progress.getSnapshot();
            assertEquals(ProgressStage.failed, summary.stage());
            assertEquals(0, summary.processedQuestions());
            assertEquals(summary.totalElapsed(), progress.getSnapshot().totalElapsed());
            assertFalse(Files.exists(config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName())));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void relocatedCamelCaseGradeStatusesRenderAsReadableLabels() {
        for (var expected : Map.of(GradeStatus.compileError, "compile error", GradeStatus.runtimeError, "runtime error").entrySet()) {
            var progress = new GradingProgress();
            progress.batchStarted(1, List.of("Q1"));
            progress.submissionStarted("student");
            progress.questionFinished("Q1", expected.getKey());
            Buffer buffer = Buffer.empty(new Rect(0, 0, 40, 10));
            StudentPanel.drawStudent(Frame.forTesting(buffer), buffer.area(), progress.getSnapshot(), 0);
            assertTrue(text(buffer).contains(expected.getValue()));
        }
    }

    @Test
    void timeoutRetainsCompletedCaseMarksInProgressAndScoresheet() throws Exception {
        Config config = new Config(fixture());
        archive("partial", "while (true) {}", "Q1/Q1.java");
        Files.writeString(directory.resolve("testers/Q1Tester.java"), """
                public class Q1Tester {
                    public static void main(String[] args) {
                        System.out.println("Test 1: completed");
                        System.out.println("Passed");
                        System.out.println("Test 2: unfinished");
                        Q1.value();
                    }
                }
                """);
        var progress = new RecordingProgress();

        var summary = JUnitRunner.run(config, progress);

        assertEquals(GradeStatus.timeout, progress.outcomes.get("partial"));
        assertEquals(2, summary.getTestsSucceededCount());
        assertEquals("1", savedMarks(config).get("partial"));
    }

    @Test
    void editableHelpersAndTrustedDataKeepNormalizerCopyPriority() throws Exception {
        Config config = new Config(fixture());
        Files.writeString(directory.resolve("templates/Q1/Helper.java"), """
                // Name:
                // Email:
                public class Helper { public static int value() { return 0; } }
                """);
        Files.writeString(directory.resolve("templates/Q1/data.txt"), "teacher");
        writeArchive("helper.zip", Map.of(
                "q1/q1.java", """
                        // Name: helper
                        // Email: helper@example.com
                        public class Q1 {
                            public static int value() {
                                try {
                                    return Helper.value() + (java.nio.file.Files.readString(java.nio.file.Path.of("data.txt")).equals("teacher") ? 0 : 1000);
                                } catch (java.io.IOException error) { throw new RuntimeException(error); }
                            }
                        }
                        """,
                "q1/helper.java", """
                        // Name: helper
                        // Email: helper@example.com
                        public class Helper { public static int value() { return 42; } }
                        """,
                "q1/data.txt", "student"));
        var progress = new RecordingProgress();

        JUnitRunner.run(config, progress);

        assertEquals(GradeStatus.completed, progress.outcomes.get("helper"));
        assertEquals("1", savedMarks(config).get("helper"));
        assertTrue(Files.readString(directory.resolve("templates/Q1/Helper.java")).contains("return 0;"));
        assertEquals("teacher", Files.readString(directory.resolve("templates/Q1/data.txt")));
    }

    @Test
    void duplicateStudentArchivesKeepHighestTotalInsteadOfLastTotal() throws Exception {
        Config config = new Config(fixture());
        archive("good", "return 42;", "Q1/Q1.java");
        Files.move(directory.resolve("submissions/good.zip"), directory.resolve("submissions/2025-2026-good.zip"));
        archive("good", "return 0;", "Q1/Q1.java");
        var progress = new RecordingProgress();

        var summary = JUnitRunner.run(config, progress);

        assertEquals(2, progress.getSnapshot().processedStudents());
        assertEquals(2, progress.getSnapshot().processedQuestions());
        assertEquals(3, summary.getTestsSucceededCount());
        assertEquals(Map.of("good", "1"), savedMarks(config));
    }

    @Test
    void scoresheetWriteFailureStopsUiAfterVerification() throws Exception {
        Config config = new Config(fixture());
        archive("good", "return 42;", "Q1/Q1.java");
        Files.writeString(config.getOutputDirectory(), "existing file");
        var progress = new RecordingProgress();

        assertThrows(IOException.class, () -> JUnitRunner.run(config, progress));

        var summary = progress.getSnapshot();
        assertEquals(ProgressStage.failed, summary.stage());
        assertEquals(1, summary.processedQuestions());
        assertEquals(2, summary.junit().passed());
        assertEquals(summary.totalElapsed(), progress.getSnapshot().totalElapsed());
        assertEquals("existing file", Files.readString(config.getOutputDirectory()));
    }

    private Map<String, String> savedMarks(Config config) throws Exception {
        Path output = config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName());
        Map<String, String> marks = new LinkedHashMap<>();
        try (var reader = new CSVReader(Files.newBufferedReader(output))) {
            List<String[]> rows = reader.readAll();
            for (String[] row : rows.subList(1, rows.size())) {
                marks.put(row[ScoreSheetColumns.username], row[ScoreSheetColumns.gradeNumerator]);
            }
        }
        return marks;
    }

    private Path fixture() throws IOException {
        Files.createDirectories(directory.resolve("templates/Q1"));
        Files.createDirectories(directory.resolve("testers"));
        Files.createDirectories(directory.resolve("submissions"));
        Files.writeString(directory.resolve("templates/Q1/Q1.java"), "public class Q1 { public static int value() { return 0; } }");
        Files.writeString(directory.resolve("testers/Q1Tester.java"), """
                public class Q1Tester {
                    public static void main(String[] args) {
                        System.out.println("Test 1: value");
                        System.out.println(Q1.value() == 42 ? "Passed" : "Failed");
                    }
                }
                """);
        Path config = directory.resolve("config.properties");
        Files.writeString(config, """
                input.directory=%s
                output.directory=%s
                submissions.folder=submissions
                testers.folder=testers
                resources.folder=templates
                scoresheet.file=roster/students.csv
                compilation.timeout.seconds=10
                execution.timeout.seconds=1
                """.formatted(directory, directory.resolve("output")).replace("\\", "\\\\"));
        return config;
    }

    private void archive(String username, String body, String entry) throws IOException {
        String source = body == null ? "no answer" : "// Name: " + username + "\n// Email: " + username + "@example.com\n"
                + "public class Q1 { public static int value() { " + body + " } }\n";
        writeArchive(username + ".zip", Map.of(entry, source));
    }

    private void writeArchive(String filename, Map<String, String> files) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(directory.resolve("submissions/" + filename)))) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String text(Buffer buffer) {
        StringBuilder result = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) result.append(buffer.get(x, y).symbol());
            result.append('\n');
        }
        return result.toString();
    }

    private static class RecordingProgress extends GradingProgress {
        final Map<String, GradeStatus> outcomes = new LinkedHashMap<>();
        Duration firstEstimate;
        int compilations;
        int executions;

        @Override
        public synchronized void compilationStarted(String question, Duration timeout) {
            super.compilationStarted(question, timeout);
            compilations++;
        }

        @Override
        public synchronized void executionStarted(String question, Duration timeout) {
            super.executionStarted(question, timeout);
            executions++;
        }

        @Override
        public synchronized void questionFinished(String question, GradeStatus status) {
            super.questionFinished(question, status);
            var snapshot = getSnapshot();
            outcomes.put(snapshot.student(), status);
            if (firstEstimate == null) firstEstimate = snapshot.estimatedTotal().orElse(null);
        }
    }
}
