package validation;

import generation.QuestionSpec;
import grading.TesterOutputParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import utils.files.FileUtils;
import utils.files.TemporaryDirectory;
import utils.process.JavaTools;
import utils.process.ProcessResult;
import validation.ValidationResult.Stage;

public class GeneratedTesterValidator {
    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final TesterOutputParser parser = new TesterOutputParser();

    public GeneratedTesterValidator(Duration compilationTimeout, Duration executionTimeout) {
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    public ValidationResult validate(QuestionSpec spec, String source, int expectedCases) {
        if (expectedCases <= 0) return ValidationResult.failed(Stage.parse, "positive marks are required");

        ValidationResult result = null;
        try (var workspace = new TemporaryDirectory("autograder-gen-validate-");
                var classes = new TemporaryDirectory("autograder-gen-classes-")) {
            result = validateInWorkspace(spec, source, expectedCases, workspace.path(), classes.path());
        } catch (IOException | RuntimeException error) {
            return workspaceFailure(result, error);
        }
        return result;
    }

    private static ValidationResult workspaceFailure(ValidationResult result, Exception error) {
        if (result == null) return ValidationResult.failed(Stage.compile, error.getMessage());
        String message = "cleanup failed > " + error.getMessage();
        return result.isValid() ? ValidationResult.failed(Stage.cleanup, message)
                : ValidationResult.failed(result.failedStage(), result.message() + " > " + message);
    }

    private ValidationResult validateInWorkspace(QuestionSpec spec, String source, int expectedCases,
            Path workspace, Path classes) {
        Stage stage = Stage.compile;
        try {
            prepareWorkspace(spec, source, workspace);

            ProcessResult compile = JavaTools.compile(workspace, classes, compilationTimeout);
            if (compile.isTimedOut()) return ValidationResult.failed(stage, "compilation timed out");
            if (!compile.isSuccessful()) return ValidationResult.failed(stage, truncate(compile.getErrorOutput()));

            stage = Stage.run;
            ProcessResult run = JavaTools.run(workspace, classes, spec.getTesterMainClass(), executionTimeout);
            if (run.isTimedOut()) return ValidationResult.failed(stage, "tester execution timed out");
            if (!run.isSuccessful()) return ValidationResult.failed(stage, truncate(run.getErrorOutput()));

            stage = Stage.parse;
            return validateOutput(run.getStandardOutput(), expectedCases);
        } catch (IOException | RuntimeException error) {
            return ValidationResult.failed(stage, error.getMessage());
        }
    }

    private void prepareWorkspace(QuestionSpec spec, String source, Path workspace) throws IOException {
        String templateName = spec.getId() + ".java";
        Files.writeString(workspace.resolve(templateName), spec.getAnswerTemplateSource());
        Files.writeString(workspace.resolve(spec.getTesterFileName()), source);

        if (spec.getResourceDirectory() == null) return;
        for (String name : spec.getSupportFiles()) {
            if (name.equals(templateName)) continue;
            Path support = spec.getResourceDirectory().resolve(name);
            if (!Files.isRegularFile(support)) throw new IOException("missing support file > " + name);
            Path destination = workspace.resolve(name);
            Files.createDirectories(destination.getParent());
            FileUtils.copyFileReplacing(support, destination);
        }
    }

    private ValidationResult validateOutput(String output, int expectedCases) {
        List<String> lines = output.strip().lines().map(String::strip).toList();

        //grading ignores outcomes before the first header > generated testers must not emit them
        boolean headerSeen = false;
        for (String line : lines) {
            if (TesterOutputParser.isTestHeader(line)) headerSeen = true;
            if (!headerSeen && TesterOutputParser.isOutcome(line)) {
                return ValidationResult.failed(Stage.parse, "outcome appeared before a test header");
            }
        }

        var cases = parser.parse(output);
        if (cases.size() != expectedCases) {
            return ValidationResult.failed(Stage.parse, "expected " + expectedCases + " cases but found " + cases.size());
        }
        long passed = cases.stream().filter(testCase -> testCase.getEarnedMarks() == 1).count();
        double total = Double.parseDouble(lines.getLast());
        if (!Double.isFinite(total) || total != passed) {
            return ValidationResult.failed(Stage.parse, "final score must equal passed-case count " + passed);
        }
        return ValidationResult.ok(cases.size());
    }

    private static String truncate(String text) {
        if (text == null || text.isBlank()) return "(no output)";
        String trimmed = text.strip();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) + "..." : trimmed;
    }
}
