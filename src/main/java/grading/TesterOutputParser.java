package grading;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.TestCaseResult;

public class TesterOutputParser { //converts tester output text into individual test-case results without running a process
    //@1ejun6 > match Test id: > capture id as group 1
    private static final Pattern TEST_HEADER = Pattern.compile("^Test ([^:]+):.*$");

    public List<TestCaseResult> parse(String output) {
        return parse(output, false);
    }

    public List<TestCaseResult> parseCompleted(String output) {
        return parse(output, true);
    }

    private List<TestCaseResult> parse(String output, boolean allowIncomplete) {
        List<TestCaseResult> results = new ArrayList<>();
        String caseId = null;
        Boolean passed = null; //@1ejun6 > null means no outcome found yet

        for (String line : output.lines().toList()) {
            String trimmedLine = line.strip(); //@1ejun6 > remove whitespace at both ends
            Matcher testHeader = TEST_HEADER.matcher(trimmedLine);
            if (testHeader.matches()) {
                //@1ejun6 > save previous test before starting next test
                if (caseId != null && (!allowIncomplete || passed != null)) {
                    results.add(createResult(caseId, passed));
                }
                caseId = testHeader.group(1);
                passed = null; //@1ejun6 > reset outcome for new test
            } else if (caseId != null && (trimmedLine.equalsIgnoreCase("Passed")
                    || trimmedLine.equalsIgnoreCase("Failed") || trimmedLine.startsWith("Failed -> "))) {
                //@1ejun6 > accept outcome lines & tester failure details after a test header
                if (passed != null) {
                    //@1ejun6 > reject more than one outcome for the same test
                    throw new IllegalArgumentException("repeated outcome for test " + caseId);
                }
                passed = trimmedLine.equalsIgnoreCase("Passed");
            }
        }
        //@1ejun6 > save final test > no next header to trigger it
        if (caseId != null && (!allowIncomplete || passed != null)) {
            results.add(createResult(caseId, passed));
        }
        //@1ejun6 > reject output with no test cases
        if (results.isEmpty() && !allowIncomplete) {
            throw new IllegalArgumentException("no test cases found in tester output");
        }
        return results;
    }

    private TestCaseResult createResult(String caseId, Boolean passed) {
        //@1ejun6 > every test header must have an outcome
        if (passed == null) {
            throw new IllegalArgumentException("missing outcome for test " + caseId);
        }
        //@1ejun6 > passed earns 1 mark > failed earns 0
        return new TestCaseResult(caseId, passed, passed ? 1 : 0, "completed");
    }
}
