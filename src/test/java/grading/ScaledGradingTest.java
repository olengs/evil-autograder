package grading;

import static org.junit.jupiter.api.Assertions.*;
import assessment.*;
import generation.QuestionSpec;
import grading.progress.ProgressListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import report.ScoreSheetWriter;
import submission.*;
import utils.files.TesterWriter;

class ScaledGradingTest {
    @TempDir Path root;

    @Test
    void scalesEightOfTenCasesToFourOfFiveAndExportsCsv() throws Exception {
        QuestionGrade grade = grade(false, true, 10);
        assertEquals(GradeStatus.completed, grade.getStatus());
        assertEquals(4, grade.getEarnedMarks(), 1e-9);
        assertEquals(4, grade.getTestCaseResults().stream().mapToDouble(TestCaseResult::getEarnedMarks).sum(), 1e-9);
        var student = new StudentSubmission(new StudentDetails("Alice", "alice", "alice"), root.resolve("alice.zip"), root);
        Path csv = root.resolve("grades.csv");
        ScoreSheetWriter.write(Map.of(student, List.of(grade)), csv);
        assertEquals("alice,4,4", Files.readAllLines(csv).get(1));
    }

    @Test
    void legacyCompletedTesterUsesObservedFullCaseCount() throws Exception {
        assertEquals(4, grade(false, false, 10).getEarnedMarks(), 1e-9);
        assertFalse(Files.exists(root.resolve("testers/numTestCases.txt")), "grading must not write counts");
    }

    @Test
    void timeoutUsesFullDeclaredDenominator() throws Exception {
        QuestionGrade grade = grade(true, true, 10);
        assertEquals(GradeStatus.timeout, grade.getStatus());
        assertEquals(1, grade.getEarnedMarks(), 1e-9);
    }

    @Test
    void timeoutWithoutCountDoesNotAwardInflatedMarks() throws Exception {
        QuestionGrade grade = grade(true, false, 10);
        assertEquals(GradeStatus.timeout, grade.getStatus());
        assertEquals(0, grade.getEarnedMarks());
        assertTrue(grade.getMessage().contains("partial marks unavailable"));
        assertFalse(Files.exists(root.resolve("testers/numTestCases.txt")));
    }

    @Test
    void declaredCountMismatchIsReported() throws Exception {
        assertEquals(GradeStatus.runtimeError, grade(false, true, 12).getStatus());
    }

    @Test
    void loaderRequiresValidMarksAndRejectsDuplicateTesterIds() throws Exception {
        Path folder = Files.createDirectories(root.resolve("Q1"));
        Files.writeString(folder.resolve("Q1aTester.java"), "class Q1aTester {}");
        assertThrows(IOException.class, () -> TesterLoader.load(root));
        Files.writeString(root.resolve("marks.txt"), "Q1a:3\nq1A:4\n");
        assertThrows(IOException.class, () -> TesterLoader.load(root));
        Files.writeString(root.resolve("marks.txt"), "Q1b:3\n");
        assertThrows(IOException.class, () -> TesterLoader.load(root));
        Files.writeString(root.resolve("marks.txt"), "Q1a:3\n");
        assertEquals(3, TesterLoader.load(root).get("q1a").getMaximumMarks());
        Files.writeString(root.resolve("Q1aTester.java"), "class Q1aTester {}");
        assertThrows(IOException.class, () -> TesterLoader.load(root));
    }

    @Test
    void generatedFilesUseQuestionFoldersAndReloadCaseMetadata() throws Exception {
        var spec = new QuestionSpec("Q1a", "class Q1a {}", root.resolve("resources/Q1"), List.of());
        Path output = root.resolve("generated");
        TesterWriter.writeValidated(spec, "class Q1aTester {}", 6, output);
        Files.writeString(output.resolve("marks.txt"), "Q1a:3\n");
        var tester = TesterLoader.load(output).get("q1a");
        assertEquals(output.resolve("Q1/Q1aTester.java"), tester.getSourceFile());
        assertEquals(6, tester.getNumTestCases());
        assertEquals(3, tester.getMaximumMarks());
    }

    private QuestionGrade grade(boolean timeout, boolean metadata, int declaredCount) throws Exception {
        Path resources = Files.createDirectories(root.resolve("resources/Q1"));
        Path answer = resources.resolve("Q1a.java");
        Files.writeString(answer, "public class Q1a {}");
        Path testers = Files.createDirectories(root.resolve("testers/Q1"));
        Files.writeString(testers.getParent().resolve("marks.txt"), "Q1a:5\n");
        if (metadata) Files.writeString(testers.getParent().resolve("numTestCases.txt"), "Q1a:" + declaredCount + "\n");
        // Reading this file verifies that only this question's sibling data is copied correctly.
        Files.writeString(testers.resolve("data.txt"), "fixture");
        Files.writeString(testers.resolve("Q1aTester.java"), """
                public class Q1aTester {
                    public static void main(String[] args) throws Exception {
                        java.nio.file.Files.readString(java.nio.file.Path.of("data.txt"));
                        for (int i = 1; i <= %d; i++) {
                            System.out.println("Test " + i + ": case");
                            System.out.println(i <= 8 ? "Passed" : "Failed");
                        }
                        %s
                    }
                }
                """.formatted(timeout ? 2 : 10, timeout ? "Thread.sleep(60000);" : "System.out.println(8);"));
        var tester = TesterLoader.load(testers.getParent()).get("q1a");
        var question = new QuestionDefinition("Q1a", "Q1", resources.getParent(), List.of("Q1a.java"), List.of());
        var attempt = new QuestionAttempt(question, resources, List.of(answer), List.of());
        return new QuestionGrader(Duration.ofSeconds(30), Duration.ofSeconds(1))
                .grade(question, attempt, tester, ProgressListener.NONE);
    }
}
