import static org.junit.jupiter.api.Assertions.*;

import assessment.*;
import grading.*;
import grading.progress.ProgressListener;
import submission.*;
import utils.files.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraderRobustnessTest {
    @TempDir Path dir;

    @Test void cleanupDoesNotFollowLinksAndRejectsReplacedRoot() throws Exception {
        Path target = Files.createDirectory(dir.resolve("target"));
        Path keep = Files.writeString(target.resolve("keep"), "safe");
        Path root = Files.createDirectory(dir.resolve("root"));
        Files.createSymbolicLink(root.resolve("link"), target);
        FileUtils.deleteDirectory(root);
        assertEquals("safe", Files.readString(keep));
        Path link = Files.createSymbolicLink(dir.resolve("root-link"), target);
        FileUtils.deleteDirectory(link);
        assertEquals("safe", Files.readString(keep));
        var temp = new TemporaryDirectory("grader-swap-test-");
        Path original = temp.path();
        Path moved = original.resolveSibling(original.getFileName() + "-moved");
        Files.move(original, moved);
        Files.createSymbolicLink(original, target);
        try {
            assertThrows(IOException.class, temp::close);
            assertEquals("safe", Files.readString(keep));
        } finally {
            Files.delete(original);
            Files.move(moved, original);
            temp.close();
        }
    }

    private QuestionGrade grade(String testerBody, boolean collision, Duration timeout) throws Exception {
        Path answers = Files.createDirectories(dir.resolve("answers"));
        Path answer = Files.writeString(answers.resolve("Q1.java"), "public class Q1 {}\n");
        if (collision) Files.writeString(answers.resolve("classes"), "student data");
        Path testerDir = Files.createDirectories(dir.resolve("testers"));
        Files.writeString(testerDir.resolve("Q1Tester.java"), "public class Q1Tester extends Q1 { public static void main(String[] args) throws Exception { " + testerBody + " } }");
        var question = new QuestionDefinition("Q1", "Q1", dir, List.of("Q1.java"), List.of());
        var attempt = new QuestionAttempt(question, answers, List.of(answer), List.of());
        return new QuestionGrader(Duration.ofSeconds(20), timeout).grade(question, attempt,
                new TesterDefinition(testerDir, Path.of("Q1Tester.java"), "Q1Tester"), ProgressListener.NONE);
    }

    @Test void classesNamedStudentFileDoesNotBlockGrading() throws Exception {
        var result = grade("System.out.println(\"Test 1: works\\nPassed\\n1\");", true, Duration.ofSeconds(5));
        assertEquals(GradeStatus.completed, result.getStatus(), result.getMessage());
        assertEquals(1, result.getEarnedMarks());
    }

    @Test void malformedOutputIsContainedOnNormalAndTimedOutRuns() throws Exception {
        String repeated = "System.out.println(\"Test 1: bad\\nPassed\\nPassed\");";
        var normal = grade(repeated, false, Duration.ofSeconds(5));
        assertEquals(GradeStatus.runtimeError, normal.getStatus());
        assertTrue(normal.getMessage().contains("invalid tester output"));
        var timedOut = grade(repeated + " Thread.sleep(10000);", false, Duration.ofSeconds(1));
        assertEquals(GradeStatus.runtimeError, timedOut.getStatus());
        assertTrue(timedOut.getMessage().contains("invalid tester output"));
    }

    @Test void invalidZipPathDoesNotPreventLoadingNextArchive() throws Exception {
        Path zips = Files.createDirectory(dir.resolve("zips"));
        Path bad = zips.resolve("a.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(bad))) {
            zip.putNextEntry(new ZipEntry("badXname"));
            zip.write(1);
            zip.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(bad);
        byte[] name = "badXname".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - name.length; i++) {
            boolean match = true;
            for (int j = 0; j < name.length; j++) if (bytes[i+j] != name[j]) match = false;
            if (match) bytes[i+3] = 0;
        }
        Files.write(bad, bytes);
        try (var zip = new ZipOutputStream(Files.newOutputStream(zips.resolve("b.zip")))) {
            zip.putNextEntry(new ZipEntry("Q1/Q1.java"));
            zip.write("public class Q1 {}".getBytes());
            zip.closeEntry();
        }
        var question = new QuestionDefinition("Q1", "Q1", dir, List.of("Q1.java"), List.of());
        var submissions = SubmissionLoader.loadSubmissions(zips, dir.resolve("extracted"), List.of(),
                new AssessmentDefinition(List.of(question)));
        assertEquals(2, submissions.size());
        assertTrue(submissions.getFirst().getSubmissionIssues().stream().anyMatch(i -> i.getMessage().contains("Invalid zip entry")));
        assertTrue(Files.exists(submissions.getLast().getRootDirectory().resolve("Q1/Q1.java")));
    }

    @Test void questionIoFailureDoesNotStopRemainingQuestions() throws Exception {
        var q1 = new QuestionDefinition("Q1", "Q1", dir, List.of("Q1.java"), List.of());
        var q2 = new QuestionDefinition("Q2", "Q2", dir, List.of("Q2.java"), List.of());
        var student = new StudentSubmission(null, dir.resolve("student.zip"), dir);
        student.addQuestionAttempt(new QuestionAttempt(q1, dir, List.of(), List.of()));
        student.addQuestionAttempt(new QuestionAttempt(q2, dir, List.of(), List.of()));
        var testers = Map.of("q1", new TesterDefinition(dir, Path.of("Q1Tester.java"), "Q1Tester"),
                "q2", new TesterDefinition(dir, Path.of("Q2Tester.java"), "Q2Tester"));
        var grader = new AssessmentGrader((question, attempt, tester, progress) -> {
            if (question.getId().equals("Q1")) throw new IOException("fixture error");
            return new QuestionGrade("Q2", GradeStatus.completed, 1, List.of(), null, "ok");
        });
        var grades = grader.grade(List.of(student), new AssessmentDefinition(List.of(q1, q2)),
                testers, ProgressListener.NONE).get(student);
        assertEquals(GradeStatus.runtimeError, grades.getFirst().getStatus());
        assertEquals(GradeStatus.completed, grades.getLast().getStatus());
    }
}
