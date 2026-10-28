import static org.junit.jupiter.api.Assertions.*;

import app.Pair1;
import grading.AssessmentGrader;
import grading.TesterLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/** Tests the real validation-to-marking flow; no reference-score file is used. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuestionGraderTest {
    @TempDir static Path temp;
    private List<StudentSubmission> submissions;
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private final Map<String, Integer> maxima = Map.of("Q1a", 3, "Q1b", 3, "Q2a", 5, "Q2b", 5, "Q3", 4);

    @BeforeAll
    void markActualSubmissions() throws Exception {
        System.out.print("project folder path > ");
        String projectPath = new Scanner(System.in).nextLine().strip();
        if (projectPath.isEmpty()) throw new IllegalArgumentException("Project folder path is required");
        Path project = Path.of(projectPath).toAbsolutePath().normalize();
        if (project.getFileName().toString().equals("student-submission")) project = project.getParent();
        Path config = Files.isDirectory(project.resolve("Project")) ? project.resolve("Project") : project;
        submissions = Pair1.run(project, temp.resolve("extracted"));
        var testers = TesterLoader.LoadTesterFiles(config.resolve("Tester-Files").toString());
        results = AssessmentGrader.grade(submissions, testers);
        for (var entry : results.entrySet()) {
            var student = entry.getKey().getStudent();
            System.out.println("\n" + (student == null ? entry.getKey().getOriginalZip().getFileName()
                : student.getName() + " (" + student.getUserName() + ")"));
            for (QuestionGrade grade : entry.getValue()) {
                System.out.printf("  %s: %.0f/%d [%s]%n", grade.getQuestionId(), grade.getEarnedMarks(),
                    maxima.get(grade.getQuestionId()), grade.getStatus());
                if (grade.getStatus() != GradeStatus.COMPLETED) System.out.println("    " + grade.getMessage());
            }
            System.out.printf("  Total: %.0f/20%n",
                entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum());
        }
    }

    @TestFactory
    Stream<DynamicTest> actualStudentQuestions() {
        return results.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(grade ->
            DynamicTest.dynamicTest(entry.getKey().getStudent().getUserName() + " " + grade.getQuestionId(), () -> {
                var attempt = entry.getKey().getQuestionAttempts().stream()
                    .filter(a -> a.getQuestionDefinition().getId().equals(grade.getQuestionId())).findFirst().orElseThrow();
                assertTrue(grade.getEarnedMarks() >= 0 && grade.getEarnedMarks() <= maxima.get(grade.getQuestionId()));
                if (!attempt.isValid()) {
                    assertEquals(GradeStatus.SKIPPED, grade.getStatus());
                    assertEquals(0, grade.getEarnedMarks());
                    assertTrue(grade.getProcessResult().isEmpty());
                    assertFalse(grade.getMessage().isBlank());
                    return;
                }
                assertNotEquals(GradeStatus.SKIPPED, grade.getStatus(), grade.getMessage());
                var process = grade.getProcessResult().orElseThrow();
                if (grade.getStatus() == GradeStatus.COMPLETED) {
                    assertTrue(process.isSuccessful());
                    assertFalse(grade.getTestCaseResults().isEmpty(), "Tester results must actually be parsed");
                    assertEquals(grade.getTestCaseResults().stream().mapToDouble(TestCaseResult::getEarnedMarks).sum(),
                        grade.getEarnedMarks());
                } else {
                    assertTrue(List.of(GradeStatus.COMPILE_ERROR, GradeStatus.RUNTIME_ERROR, GradeStatus.TIMEOUT)
                        .contains(grade.getStatus()));
                    assertEquals(0, grade.getEarnedMarks());
                    assertFalse(process.isSuccessful());
                    assertEquals(grade.getStatus() == GradeStatus.TIMEOUT, process.isTimedOut());
                }
            })));
    }

    @Test
    void allActualStudentsAndTotalsAreIncluded() {
        assertFalse(submissions.isEmpty(), "The selected folder must contain submissions");
        assertEquals(submissions.size(), results.size());
        assertEquals(submissions.size() * 5, results.values().stream().mapToInt(List::size).sum());
        for (var entry : results.entrySet()) {
            assertNotNull(entry.getKey().getStudent());
            assertEquals(5, entry.getValue().size());
            assertTrue(entry.getKey().getQuestionAttempts().stream().allMatch(QuestionAttempt::isValid),
                "The supplied question folders must be found, including inside ZIP wrappers");
            double total = entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum();
            assertTrue(total >= 0 && total <= 20);
        }
        assertTrue(results.values().stream().flatMap(List::stream)
            .anyMatch(g -> g.getStatus() == GradeStatus.COMPLETED && g.getEarnedMarks() > 0),
            "The integration must actually compile and execute answers, not return zero for everything");
    }

    @Test
    void invalidSubmissionIsSkippedWithAReason() throws Exception {
        var actual = submissions.get(0);
        Path zips = Files.createDirectory(temp.resolve("invalid-zip"));
        try (var zip = new ZipOutputStream(Files.newOutputStream(zips.resolve(actual.getStudent().getUserName() + ".zip")))) {
            // Empty ZIP deliberately has no required question folder or answer.
        }
        var definition = actual.getQuestionAttempts().get(0).getQuestionDefinition();
        var invalid = Pair1.run(zips, temp.resolve("invalid-extracted"), List.of(actual.getStudent()),
            new AssessmentDefinition("Missing answer", List.of(definition)));
        var grade = AssessmentGrader.grade(invalid, Map.of()).values().iterator().next().get(0);
        assertEquals(GradeStatus.SKIPPED, grade.getStatus());
        assertEquals(0, grade.getEarnedMarks());
        assertTrue(grade.getProcessResult().isEmpty(), "Invalid answers must not be compiled or run");
        assertTrue(grade.getMessage().contains("missing"));
    }
}
