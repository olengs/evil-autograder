package generation.validation;

public final class ValidationResult { //outcome of validating one generated tester > ok or why it failed
    // stage that failed > lets the orchestrator feed a useful hint back into a retry prompt
    public enum Stage { COMPILE, RUN, PARSE, NONE }

    private final boolean valid;
    private final Stage failedStage;
    private final String message; // compiler/runtime/parser detail > useful for retry prompts
    private final int testCaseCount; // parsed test cases when it reached the parse stage

    private ValidationResult(boolean valid, Stage failedStage, String message, int testCaseCount) {
        this.valid = valid;
        this.failedStage = failedStage;
        this.message = message == null ? "" : message;
        this.testCaseCount = testCaseCount;
    }

    public static ValidationResult ok(int testCaseCount) {
        return new ValidationResult(true, Stage.NONE, "compiled, ran and parsed", testCaseCount);
    }

    public static ValidationResult failed(Stage stage, String message) {
        return new ValidationResult(false, stage, message, 0);
    }

    public boolean isValid() {
        return valid;
    }

    public Stage getFailedStage() {
        return failedStage;
    }

    public String getMessage() {
        return message;
    }

    public int getTestCaseCount() {
        return testCaseCount;
    }

    @Override
    public String toString() {
        return valid
                ? "ValidationResult{valid, testCases=" + testCaseCount + '}'
                : "ValidationResult{failed at " + failedStage + " > " + message + '}';
    }
}
