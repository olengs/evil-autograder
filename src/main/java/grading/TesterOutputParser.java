package grading;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TesterOutputParser { //parses tester cases & keeps completed outcomes after a timeout
    private static final Pattern testHeader = Pattern.compile("^Test ([^:]+):.*$");

    public static boolean isTestHeader(String line) {
        return testHeader.matcher(line.strip()).matches();
    }

    public static boolean isOutcome(String line) {
        String trimmed = line.strip();
        return trimmed.equalsIgnoreCase("Passed") || trimmed.equalsIgnoreCase("Failed") || trimmed.startsWith("Failed -> ");
    }

    public List<TestCaseResult> parse(String output) {
        return parse(output, false);
    }

    //used after a timeout > unfinished cases are omitted > malformed completed cases still fail
    public List<TestCaseResult> parseCompleted(String output) {
        return parse(output, true);
    }

    private List<TestCaseResult> parse(String output, boolean ignoreIncompleteCases) {
        List<TestCaseResult> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        String caseId = null;
        Boolean passed = null; //no outcome has been reported for current case yet

        for (String line : output.lines().toList()) {
            String trimmedLine = line.strip();
            Matcher headerMatch = testHeader.matcher(trimmedLine);
            if (headerMatch.matches()) {
                if (caseId != null && (!ignoreIncompleteCases || passed != null)) results.add(createResult(caseId, passed));
                caseId = headerMatch.group(1).strip();

                if (!seenIds.add(caseId)) throw new IllegalArgumentException("duplicate test id " + caseId);
                passed = null;
            } else if (caseId != null && isOutcome(trimmedLine)) {
                if (passed != null) throw new IllegalArgumentException("repeated outcome for test " + caseId);
                passed = trimmedLine.equalsIgnoreCase("Passed");
            }
        }
        //last case has no following header to trigger saving it
        if (caseId != null && (!ignoreIncompleteCases || passed != null)) results.add(createResult(caseId, passed));
        if (results.isEmpty() && !ignoreIncompleteCases) throw new IllegalArgumentException("no test cases found in tester output");
        return results;
    }

    private TestCaseResult createResult(String caseId, Boolean passed) {
        if (passed == null) {
            throw new IllegalArgumentException("missing outcome for test " + caseId);
        }
        //each passed case earns one mark > a failed case earns zero
        return new TestCaseResult(passed ? 1 : 0);
    }
}
