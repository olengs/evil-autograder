package generation;

import static org.junit.jupiter.api.Assertions.*;
import assessment.*;
import grading.*;
import grading.progress.ProgressListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import openai.GeneratedTesters.ResourceFile;
import submission.QuestionAttempt;
import utils.files.TesterResources;
import utils.files.TesterWriter;
import utils.logging.JsonLineLogger;
import validation.GeneratedTesterValidator;

class GeneratedResourcesTest {
    @TempDir Path root;

    @Test
    void resourceTravelsFromResponseThroughValidationAndWritingToGrading() throws Exception {
        var log = JsonLineLogger.getInstance();
        log.Init(root.resolve("log.jsonl"));
        Path answers = Files.createDirectories(root.resolve("answers/Q2"));
        Path answer = answers.resolve("Q2a.java");
        Files.writeString(answer, "public class Q2a {}");
        var spec = new QuestionSpec("Q2a", Files.readString(answer), answers, List.of());
        var data = resource("personstester.txt", "Alice,20\n");
        String source = """
                public class Q2aTester {
                    public static void main(String[] args) throws Exception {
                        String data = java.nio.file.Files.readString(java.nio.file.Path.of("personstester.txt"));
                        System.out.println("Test 1: input");
                        boolean passed = data.equals("Alice,20\\n");
                        System.out.println(passed ? "Passed" : "Failed");
                        System.out.println(passed ? 1 : 0);
                    }
                }
                """;
        try {
            var validator = new GeneratedTesterValidator(Duration.ofSeconds(30), Duration.ofSeconds(5));
            assertFalse(validator.validate(spec, source, 1, 1).isValid());
            assertTrue(validator.validate(spec, source, 1, 1, List.of(data)).isValid());
            Path output = root.resolve("testers");
            TesterWriter.prepareOutput(List.of(spec), Map.of("q2a", 1.0), output);
            TesterWriter.writeValidated(spec, source, 1, List.of(data), output);
            assertEquals(data.content, Files.readString(output.resolve("Q2/personstester.txt")));
            var question = new QuestionDefinition("Q2a", "Q2", answers.getParent(), List.of("Q2a.java"), List.of());
            var attempt = new QuestionAttempt(question, answers, List.of(answer), List.of());
            var grade = new QuestionGrader(Duration.ofSeconds(30), Duration.ofSeconds(5))
                    .grade(question, attempt, TesterLoader.load(output).get("q2a"), ProgressListener.NONE);
            assertEquals(GradeStatus.completed, grade.getStatus());
            assertEquals(1, grade.getEarnedMarks());
            Path review = TesterWriter.writeForReview(spec, source, List.of(data), output);
            assertEquals(data.content, Files.readString(review.getParent().resolve("personstester.txt")));
        } finally {
            log.flush();
        }
    }

    @Test
    void rejectsEscapingExecutableDuplicateAndSuppliedResourceNames() {
        var spec = new QuestionSpec("Q2a", "class Q2a {}", null, List.of("provided.txt"));
        for (String name : List.of("../escape.txt", "/absolute.txt", "Q2a.java", "Q2a.class", "provided.txt")) {
            assertThrows(IOException.class, () -> TesterResources.validate(spec, List.of(resource(name, "data"))));
        }
        assertThrows(IOException.class, () -> TesterResources.validate(spec,
                List.of(resource("data.txt", "one"), resource("DATA.txt", "two"))));
    }

    private ResourceFile resource(String name, String content) {
        var file = new ResourceFile();
        file.fileName = name;
        file.content = content;
        return file;
    }
}
