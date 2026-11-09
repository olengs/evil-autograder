package validation;

public record ValidationResult(Stage failedStage, String message, int testCaseCount) {
    public enum Stage { compile, run, parse, cleanup, passed }

    public ValidationResult {
        message = message == null ? "" : message;
    }

    public static ValidationResult ok(int testCaseCount) {
        return new ValidationResult(Stage.passed, "compiled & ran & parsed", testCaseCount);
    }

    public static ValidationResult failed(Stage stage, String message) {
        return new ValidationResult(stage, message, 0);
    }

    public boolean isValid() {
        return failedStage == Stage.passed;
    }
}
