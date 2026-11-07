import static org.junit.jupiter.api.Assertions.*;

import generation.questionSpec.QuestionSpec;
import generation.validation.GeneratedTesterValidator;
import generation.validation.ValidationResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import utils.TemplateUtils;

class GeneratedTesterValidatorTest {
    private final GeneratedTesterValidator validator = new GeneratedTesterValidator(Duration.ofSeconds(30), Duration.ofSeconds(2));

    private QuestionSpec spec(int answer) {
        return new QuestionSpec("Q1", "public class Q1 { public static int answer() { return " + answer + "; } }", null, List.of());
    }

    private String program(String body) {
        return "public class Q1Tester extends Q1 { public static void main(String[] args) throws Exception { " + body + " } }";
    }

    @Test
    void acceptsCorrectAndDeliberatelyIncorrectAnswersWhenProtocolIsConsistent() {
        String code = program("""
                System.out.println("Test 1: answer");
                boolean passed = answer() == 42;
                System.out.println(passed ? "Passed" : "Failed");
                System.out.println(passed ? 1 : 0);
                """);
        assertTrue(validator.validate(spec(42), code, 1).isValid());
        assertTrue(validator.validate(spec(0), code, 1).isValid());
    }

    @Test
    void rejectsWrongCaseCountWrongTotalAndOrphanOutcomes() {
        String output = "System.out.println(\"Test 1: answer\\nPassed\\n1.0\");";
        assertFalse(validator.validate(spec(42), program(output), 2).isValid());
        assertFalse(validator.validate(spec(42), program(output.replace("1.0", "2.0")), 1).isValid());
        assertFalse(validator.validate(spec(42), program(output.replace("1.0", "NaN")), 1).isValid());
        assertFalse(validator.validate(spec(42), program("System.out.println(\"Failed\");" + output), 1).isValid());
        assertFalse(validator.validate(spec(42), program("System.out.println(\"Test 1: missing outcome\\n0\");"), 1).isValid());
    }

    @Test
    void rejectsTimeoutEvenAfterACompletedCase() {
        String code = program("System.out.println(\"Test 1: answer\\nPassed\\n1\"); Thread.sleep(10000);");
        var result = validator.validate(spec(42), code, 1);
        assertFalse(result.isValid());
        assertEquals(ValidationResult.Stage.RUN, result.getFailedStage());
    }

    @Test
    void templateRecordsSetupExceptionsAndExpectedExceptionsExactlyOnce() {
        String caseTemplate = TemplateUtils.readResource("/generation/tester/tester-case.java.txt");
        String setupFailure = TemplateUtils.fill(caseTemplate, java.util.Map.of(
                "methodName", "answer", "setupCode", "if (System.nanoTime() != 0) throw new IllegalStateException();",
                "ReturnType", "int", "expectedValue", "42", "arguments", "", "comparison", "expected == result"));
        String expectedException = TemplateUtils.fill(caseTemplate, java.util.Map.of(
                "methodName", "answer", "setupCode", "",
                "ReturnType", "int", "expectedValue", "42", "arguments", "", "comparison", "expected == result"))
                .replace("int result = answer();", "int result = 0; try { throw new IllegalArgumentException(); } catch (IllegalArgumentException expectedException) { result = 42; }");
        String code = TemplateUtils.fill(TemplateUtils.readResource("/generation/tester/tester-skeleton.java.txt"),
                java.util.Map.of("imports", "", "questionId", "Q1", "testCases", setupFailure + expectedException, "helperCode", ""));
        assertTrue(validator.validate(spec(42), code, 2).isValid());
    }
}
