package grading;

/// extracts useful tester lines after process execution
public final class TesterOutputExtractor { 
    public ExtractedTesterOutput extract(String standardOutput) {
        StringBuilder testOutput = new StringBuilder();
        String lastLine = "";
        boolean seenHeader = false;
        boolean outcomeBeforeHeader = false;

        for (String line : standardOutput.lines().toList()) {
            String trimmedLine = line.strip();
            if (!trimmedLine.isEmpty()) lastLine = trimmedLine;

            if (TesterOutputParser.isTestHeader(trimmedLine)) {
                seenHeader = true;
                testOutput.append(trimmedLine).append('\n');
            } else if (TesterOutputParser.isOutcome(trimmedLine)) {
                if (!seenHeader) outcomeBeforeHeader = true;
                testOutput.append(trimmedLine).append('\n');
            }
        }
        // Preserve duplicate headers and outcomes so the parser can reject them.
        return new ExtractedTesterOutput(testOutput.toString(), lastLine, outcomeBeforeHeader);
    }
}
