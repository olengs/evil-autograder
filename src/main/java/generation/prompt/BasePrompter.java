package generation.prompt;

public final class BasePrompter implements Prompter { //the default contract every generated tester must follow
    public static final String ID = "base";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String instructions() {
        // these rules come straight from the real tester files & TesterOutputParser > they are mandatory
        // TODO need refinement? put it in .properties?
        return """
                You are generating a Java tester program used by an automated grader.
                Follow every rule exactly. The grader parses your program's console output.

                CLASS AND STRUCTURE RULES:
                - Declare: public class <QuestionId>Tester extends <QuestionId>
                    (the tester extends the student's answer class and inherits its static methods).
                - Do NOT write a package statement.
                - Do NOT import anything beyond standard java.util.* (no external libraries).
                - Hold a private static double score field, starting at 0.
                - Provide: public static void main(String[] args) that calls grade() then prints score on its own line.
                - Put all test cases inside a static grade() method.

                TEST CASE RULES:
                - Wrap EACH test case in its own try/catch(Exception).
                - Before running a case, print a header line that starts with: Test <n>:
                    Example: System.out.printf("Test %d: methodName(%s)%n", tcNum++, inputs);
                    Each test number must be unique.
                - After the call, print the expected value and the actual value for debugging.
                - Print EXACTLY ONE outcome line per test case:
                    "Passed"  when the actual result equals the expected result,
                    "Failed"  when it does not,
                    "Failed -> <reason>" inside the catch block when an exception is thrown.
                - On a pass, do: score += 1;  (one mark per passing test case).

                OUTPUT RULES:
                - The grader only understands the "Test <n>:" header and the "Passed"/"Failed"/"Failed -> ..." lines.
                - Do not print anything that could be mistaken for those lines.

                RESPONSE RULES:
                - Return ONLY the Java source code for the tester class.
                - No explanation, no markdown fences, no prose before or after the code.
                """;
    }
}
