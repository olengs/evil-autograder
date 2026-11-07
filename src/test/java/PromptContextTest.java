import static org.junit.jupiter.api.Assertions.*;

import generation.prompt.PromptBuilder;
import generation.questionSpec.QuestionSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptContextTest {
    @TempDir Path dir;

    @Test
    void includesSharedDataApisAndDocumentationOnceAndExcludesGradesAndSubmissions() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("Q1"));
        Files.writeString(folder.resolve("data.txt"), "unique fixture data");
        Path java = Files.writeString(folder.resolve("Dependency.java"),
                "public class Dependency { public double area() { return 3.14; } }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, java.toString()));
        Files.createDirectories(folder.resolve("api"));
        Files.writeString(folder.resolve("api/Dependency.html"),
                "<html><body><nav>navigation noise</nav><main><h2>area()</h2><p>Uses 3.14 &amp; radius.</p><script>script noise</script></main></body></html>");
        Files.writeString(folder.resolve("groundtruth.txt"), "GRADE_SECRET");
        Files.writeString(folder.resolve("Q1aTester.java"), "EXISTING_TESTER_SECRET");
        Files.createDirectories(dir.resolve("student-submission"));
        Files.writeString(dir.resolve("student-submission/student.java"), "SUBMISSION_SECRET");
        var supports = List.of("data.txt", "Dependency.java", "Dependency.class", "groundtruth.txt", "Q1aTester.java");
        var specs = List.of(new QuestionSpec("Q1a", "public class Q1a {}", folder, supports),
                new QuestionSpec("Q1b", "public class Q1b {}", folder, supports));
        var bundle = new PromptBuilder().collectFiles(specs);
        assertTrue(bundle.issuesByQuestion().isEmpty(), bundle.issuesByQuestion().toString());
        assertEquals(1, bundle.text().split("unique fixture data", -1).length - 1);
        assertTrue(bundle.text().contains("public double area();"));
        assertTrue(bundle.text().contains("Uses 3.14 & radius."));
        assertFalse(bundle.text().contains("navigation noise"));
        assertFalse(bundle.text().contains("script noise"));
        assertFalse(bundle.text().contains("_SECRET"));
        String prompt = new PromptBuilder().build(specs, bundle.text(), "", null, true);
        assertTrue(prompt.contains("within this count"));
        assertTrue(prompt.contains("Return only JSON"));
        assertFalse(prompt.contains("Return ONLY the Java"));
        assertFalse(prompt.contains("at least 3"));
    }

    @Test
    void missingSupportFileFlagsEveryAffectedSubpart() {
        var specs = List.of(new QuestionSpec("Q1a", "public class Q1a {}", dir, List.of("missing.txt")),
                new QuestionSpec("Q1b", "public class Q1b {}", dir, List.of("missing.txt")));
        var bundle = new PromptBuilder().collectFiles(specs);
        assertEquals(2, bundle.issuesByQuestion().size());
        assertTrue(bundle.text().contains("CONTEXT ISSUE"));
    }
}
