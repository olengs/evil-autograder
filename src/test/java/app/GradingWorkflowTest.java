package app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import config.Config;
import grading.GradeStatus;
import grading.progress.GradingProgress;
import grading.progress.ProgressStage;
import support.GradingFixture;
import utils.logging.JsonLineLogger;

class GradingWorkflowTest {
    @TempDir Path directory;
    Config config;

    @BeforeEach
    void setup() throws Exception {
        config = GradingFixture.create(directory);
        JsonLineLogger.getInstance().Init(directory.resolve("test.log"));
    }

    @AfterEach
    void flushLogs() throws Exception {
        JsonLineLogger.getInstance().flush();
    }

    @Test
    void gradesAndExportsBeforeReportingCompletion() throws Exception {
        Path csv = config.getOutputDirectory().resolve("grades.csv");
        List<ProgressStage> stages = new ArrayList<>();
        var progress = new GradingProgress() {
            @Override public synchronized void batchFinished() {
                super.batchFinished();
                assertFalse(getSnapshot().isFinished(), "export is still pending");
                stages.add(getSnapshot().stage());
            }
            @Override public synchronized void completed() {
                assertTrue(Files.isRegularFile(csv), "CSV must exist before completion");
                super.completed();
            }
        };
        var results = GradingWorkflow.run(config, progress);
        assertEquals(3, results.size());
        var grades = results.values().stream().map(List::getFirst).toList();
        assertEquals(List.of(GradeStatus.completed, GradeStatus.completed, GradeStatus.compileError),
                grades.stream().map(grade -> grade.getStatus()).toList());
        assertEquals(List.of(1.0, 0.0, 0.0), grades.stream().map(grade -> grade.getEarnedMarks()).toList());
        assertEquals(4, Files.readAllLines(csv).size());
        assertEquals(List.of(ProgressStage.preparing), stages);
        assertEquals(ProgressStage.completed, progress.getSnapshot().stage());
        assertEquals(3, progress.getSnapshot().processedStudents());
        assertTrue(progress.getSnapshot().activeStudents().isEmpty());
    }

    @Test
    void exportFailureIsReportedAsFailure() throws Exception {
        Files.writeString(config.getOutputDirectory(), "blocks the output directory");
        var progress = new GradingProgress();
        assertThrows(IOException.class, () -> GradingWorkflow.run(config, progress));
        assertEquals(3, progress.getSnapshot().processedStudents());
        assertEquals(ProgressStage.failed, progress.getSnapshot().stage());
    }

    @Test
    void cancellationDoesNotExport() {
        var progress = new GradingProgress();
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> GradingWorkflow.run(config, progress));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(ProgressStage.failed, progress.getSnapshot().stage());
            assertFalse(Files.exists(config.getOutputDirectory()));
        } finally {
            Thread.interrupted();
        }
    }
}
