import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import generation.output.TesterSourceRenderer;
import generation.questionSpec.QuestionSpec;
import generation.schema.GeneratedTesterSpec;
import generation.validation.GeneratedTesterValidator;
import generation.validation.ValidationResult;


class TesterSourceRendererTest {
    private static final String TEMPLATE = """
            import java.util.*;
            public class Q9 {
                public static int[] doubled(int[] xs) { int[] r = new int[xs.length]; for (int i = 0; i < xs.length; i++) r[i] = xs[i] * 2; return r; }
                public static int add(int a, int b) { return a + b; }
                public static int boom(int a) { throw new IllegalStateException("bad\\nline"); }
            }
            """;

    private final QuestionSpec spec = new QuestionSpec("Q9", TEMPLATE, null, List.of());
    private final TesterSourceRenderer renderer = new TesterSourceRenderer();

    private static GeneratedTesterSpec.TestCase testCase(String label, String setup, String actual, String expected) {
        GeneratedTesterSpec.TestCase c = new GeneratedTesterSpec.TestCase();
        c.label = label;
        c.setupCode = setup;
        c.actualExpression = actual;
        c.expectedExpression = expected;
        return c;
    }

    @Test
    void renderedTesterCompilesRunsAndParses() {
        GeneratedTesterSpec generated = new GeneratedTesterSpec();
        generated.imports = List.of("import java.util.*;");
        generated.helperCode = "";
        generated.testCases = List.of(
                testCase("add(2, \"x\\y\")", "int a = 2;", "add(a, 3);", "5"),
                testCase("doubled([1, 2])", "int[] in = {1, 2};", "doubled(in)", "new int[] {2, 4}"),
                testCase("add wrong", "", "add(1, 1)", "3"),
                testCase("boom", "", "boom(1)", "0"));

        ValidationResult result = new GeneratedTesterValidator(Duration.ofSeconds(60), Duration.ofSeconds(30))
                .validate(spec, renderer.render(spec, generated));

        assertTrue(result.isValid(), result.toString());
        assertEquals(4, result.getTestCaseCount());
    }

    @Test
    void rejectsUnusableReplies() {
        GeneratedTesterSpec empty = new GeneratedTesterSpec();
        empty.testCases = List.of();
        assertThrows(IllegalArgumentException.class, () -> renderer.render(spec, empty));

        GeneratedTesterSpec external = new GeneratedTesterSpec();
        external.imports = List.of("import org.apache.commons.lang3.*;");
        external.testCases = List.of(testCase("a", "", "add(1, 1)", "2"));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(spec, external));

        GeneratedTesterSpec blank = new GeneratedTesterSpec();
        blank.testCases = List.of(testCase("a", "", " ; ", "2"));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(spec, blank));
    }
}
