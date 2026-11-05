package generation.output;

import java.util.List;
import java.util.Map;

import generation.questionSpec.QuestionSpec;
import generation.schema.GeneratedTesterSpec;
import utils.TemplateUtils;

public class TesterSourceRenderer { //fills the tester templates with the llm's test cases > so the grader format cannot drift
    // the fixed tester layout lives in these files > edit them to change what every generated tester looks like
    private static final String SKELETON_RESOURCE = "/generation/tester/tester-skeleton.java.txt";
    private static final String CASE_RESOURCE = "/generation/tester/tester-case.java.txt";

    private final String skeleton = TemplateUtils.readResource(SKELETON_RESOURCE);
    private final String caseBlock = TemplateUtils.readResource(CASE_RESOURCE);

    // build the full tester source > throw IllegalArgumentException when the model's json is unusable so the caller can retry
    public String render(QuestionSpec spec, GeneratedTesterSpec generated) {
        if (generated == null || generated.testCases == null || generated.testCases.isEmpty()) {
            throw new IllegalArgumentException("structured reply contained no test cases");
        }

        StringBuilder cases = new StringBuilder();
        for (GeneratedTesterSpec.TestCase testCase : generated.testCases) {
            cases.append(renderCase(testCase));
        }

        return TemplateUtils.fill(skeleton, Map.of(
                "imports", String.join("\n", orEmpty(generated.imports).stream().map(this::requireJdkImport).toList()),
                "testerClass", spec.getTesterMainClass(),
                "questionId", spec.getId(),
                "helperCode", blank(generated.helperCode) ? "" : indent(generated.helperCode.strip(), 4) + "\n",
                "testCases", cases.toString().stripTrailing()));
    }

    private String renderCase(GeneratedTesterSpec.TestCase testCase) {
        String label = blank(testCase.label) ? "test case" : testCase.label.strip();
        return TemplateUtils.fill(caseBlock, Map.of(
                "label", javaLiteral(label),
                "setupCode", blank(testCase.setupCode) ? "" : indent(testCase.setupCode.strip(), 12),
                "expected", requireExpression(testCase.expectedExpression, "expectedExpression"),
                "actual", requireExpression(testCase.actualExpression, "actualExpression")));
    }

    // only java.* imports > matches the base rule of no third-party libraries
    private String requireJdkImport(String line) {
        String trimmed = line == null ? "" : line.strip();
        if (!trimmed.startsWith("import java.") || !trimmed.endsWith(";")) {
            throw new IllegalArgumentException("import must be a java standard library import ending with ; > " + trimmed);
        }
        return trimmed;
    }

    // one expression without a trailing semicolon > the template adds its own
    private String requireExpression(String expression, String field) {
        String trimmed = expression == null ? "" : expression.strip();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("test case " + field + " is empty");
        }
        return trimmed;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String indent(String block, int spaces) {
        String pad = " ".repeat(spaces);
        return block.lines().map(line -> pad + line).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    // a java string literal for arbitrary text > one line
    private static String javaLiteral(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n', '\r' -> out.append(' ');
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
