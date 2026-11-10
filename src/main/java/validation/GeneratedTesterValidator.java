package validation;

import generation.QuestionSpec;
import openai.GeneratedTesters.ResourceFile;
import utils.files.TesterResources;
import grading.TesterOutputParser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import utils.files.FileUtils;
import utils.files.TemporaryDirectory;
import utils.logging.JsonLineLogger;
import utils.process.JavaTools;
import utils.process.ProcessResult;
import utils.process.Interruptions;
import validation.ValidationResult.Stage;

public class GeneratedTesterValidator {
    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final TesterOutputParser parser = new TesterOutputParser();

    public GeneratedTesterValidator(Duration compilationTimeout, Duration executionTimeout) {
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    public ValidationResult validate(QuestionSpec spec, String source, int expectedCases, double marks) throws InterruptedIOException {
        return validate(spec, source, expectedCases, marks, List.of());
    }

    public ValidationResult validate(QuestionSpec spec, String source, int expectedCases, double marks,
            List<ResourceFile> resources) throws InterruptedIOException {
        Interruptions.check("tester validation interrupted");
        if ((!Double.isFinite(marks * 2) || marks <= 0 || marks * 2 != Math.floor(marks * 2))) return failed(Stage.parse, "marks must be positive whole or half marks");
        if (expectedCases <= 0 || expectedCases % marks != 0) {
            return failed(Stage.parse, "numTestCases must be a positive multiple of marks");
        }
        return validateWithResources(spec, source, expectedCases, resources);
    }

    public ValidationResult validate(QuestionSpec spec, String source, int expectedCases) throws InterruptedIOException {
        return validateWithResources(spec, source, expectedCases, List.of());
    }

    private ValidationResult validateWithResources(QuestionSpec spec, String source, int expectedCases,
            List<ResourceFile> resources) throws InterruptedIOException {
        Interruptions.check("tester validation interrupted");
        if (expectedCases <= 0) return failed(Stage.parse, "positive test-case count is required");

        ValidationResult result = null;
        try (var workspace = new TemporaryDirectory("autograder-gen-validate-");
                var classes = new TemporaryDirectory("autograder-gen-classes-")) {
            result = validateInWorkspace(spec, source, expectedCases, workspace.path(), classes.path(), resources);
        } catch (IOException | RuntimeException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "GeneratedTesterValidator", error.getMessage());
            Interruptions.check("tester validation interrupted");
            return workspaceFailure(result, error);
        }
        return result;
    }

    private static ValidationResult workspaceFailure(ValidationResult result, Exception error) {
        if (result == null) return failed(Stage.compile, error.getMessage());
        String message = "cleanup failed > " + error.getMessage();
        return result.isValid() ? failed(Stage.cleanup, message)
                : failed(result.failedStage(), result.message() + " > " + message);
    }

    private ValidationResult validateInWorkspace(QuestionSpec spec, String source, int expectedCases,
            Path workspace, Path classes, List<ResourceFile> resources) throws InterruptedIOException {
        Stage stage = Stage.compile;
        try {
            prepareWorkspace(spec, source, workspace);
            TesterResources.write(spec, resources, workspace);

            ProcessResult compile = JavaTools.compile(workspace, classes, compilationTimeout);
            if (compile.isTimedOut()) return failed(stage, "compilation timed out");
            if (!compile.isSuccessful()) return failed(stage, truncate(compile.getErrorOutput()));

            stage = Stage.run;
            Interruptions.check("tester validation interrupted");
            ProcessResult run = JavaTools.run(workspace, classes, spec.testerMainClass(), executionTimeout);
            if (run.isTimedOut()) return failed(stage, "tester execution timed out");
            if (!run.isSuccessful()) return failed(stage, truncate(run.getErrorOutput()));

            stage = Stage.parse;
            Interruptions.check("tester validation interrupted");
            return validateOutput(run.getStandardOutput(), expectedCases);
        } catch (IOException | RuntimeException error) {
            JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR, "GeneratedTesterValidator", error.getMessage());
            Interruptions.check("tester validation interrupted");
            return failed(stage, error.getMessage());
        }
    }

    private void prepareWorkspace(QuestionSpec spec, String source, Path workspace) throws IOException {
        String templateName = spec.id() + ".java";
        Files.writeString(workspace.resolve(templateName), spec.answerTemplateSource());
        Files.writeString(workspace.resolve(spec.testerFileName()), source);

        if (spec.resourceDirectory() == null) return;
        for (String name : spec.supportFiles()) {
            Interruptions.check("tester validation interrupted");
            if (name.equals(templateName)) continue;
            Path support = spec.resourceDirectory().resolve(name);
            if (!Files.isRegularFile(support)) throw new IOException("missing support file > " + name);
            Path destination = workspace.resolve(name);
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
                return failed(Stage.parse, "outcome appeared before a test header");
            }
        }

        var cases = parser.parse(output);
        if (cases.size() != expectedCases) {
            return failed(Stage.parse, "expected " + expectedCases + " cases but found " + cases.size());
        }
        long passed = cases.stream().filter(testCase -> testCase.getEarnedMarks() == 1).count();
        double total = Double.parseDouble(lines.getLast());
        if (!Double.isFinite(total) || total != passed) {
            return failed(Stage.parse, "final score must equal passed-case count " + passed);
        }
        return ValidationResult.ok(cases.size());
    }

    private static ValidationResult failed(Stage stage, String message) {
        JsonLineLogger.getInstance().append(JsonLineLogger.Status.ERROR,
                "GeneratedTesterValidator", stage + " > " + message);
        return ValidationResult.failed(stage, message);
    }

    private static String truncate(String text) {
        if (text == null || text.isBlank()) return "(no output)";
        String trimmed = text.strip();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) + "..." : trimmed;
    }
}
