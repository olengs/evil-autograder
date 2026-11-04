package generation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// the output format sent to the model > the openai sdk builds the strict json schema from this class
// the descriptions are written from the base prompt rules > TesterSourceRenderer then writes the fixed parts of the tester
@JsonClassDescription("The test cases for one question's tester. The tester class, score field, main(), "
        + "'Test <n>:' headers and Passed/Failed outcome lines are generated for you > never write them.")
public class GeneratedTesterSpec {
    @JsonPropertyDescription("The question id this tester is for, exactly as listed in the prompt, e.g. \"Q1a\". "
            + "The tester is written to <questionId>Tester.java.")
    public String questionId;

    @JsonPropertyDescription("Import statements, one per entry, e.g. \"import java.util.*;\". "
            + "Java standard library only (java.* packages), no third-party libraries. Empty list when none are needed.")
    public List<String> imports;

    @JsonPropertyDescription("Optional extra static fields or methods the test cases use, written as members of the tester class. "
            + "Empty string when none. Do not declare a class, main(), grade() or a score field.")
    public String helperCode;

    @JsonPropertyDescription("The test cases > exactly one per mark in the question's mark allocation "
            + "(e.g. [3 marks] = 3 test cases) > at least 3 when the marks are unclear. Each case is worth one mark.")
    public List<TestCase> testCases;

    @JsonClassDescription("One independent test case > run in its own try/catch so one failure never stops the others.")
    public static class TestCase {
        @JsonPropertyDescription("Text printed after 'Test <n>: ', e.g. \"reverse([1, 2, 3])\". "
                + "One line of plain text describing the call and its inputs.")
        public String label;

        @JsonPropertyDescription("Java statements that build brand-new inputs for this case only, never reused from another case. "
                + "Use DIFFERENT inputs from the answer template's own main method and the question examples, "
                + "so hardcoded answers fail. Empty string when no setup is needed. "
                + "Do not print anything. Do not declare variables named expected, actual, tcNum or e.")
        public String setupCode;

        @JsonPropertyDescription("One Java expression, no trailing semicolon, that calls the student's method and yields the actual result. "
                + "The tester extends the answer class, so call its static methods without a class name.")
        public String actualExpression;

        @JsonPropertyDescription("One Java expression, no trailing semicolon, for the expected result, computed from the documented behaviour "
                + "of the question and not copied from the template's examples. Arrays and collections are compared by content.")
        public String expectedExpression;
    }
}
