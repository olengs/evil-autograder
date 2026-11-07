package generation.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import generation.questionSpec.QuestionSpec;
import generation.validation.ValidationResult.Stage;
import grading.TesterOutputParser;
import utils.process.ProcessResult;
import grading.TestCaseResult;
import utils.files.FileUtils;
import utils.files.TemporaryDirectory;
import utils.process.JavaTools;

//!update
public class GeneratedTesterValidator { //checks a generated tester actually compiles, runs & parses against its template
    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final TesterOutputParser parser = new TesterOutputParser();

    public GeneratedTesterValidator(Duration compilationTimeout, Duration executionTimeout) {
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    //validate one generated tester against the question's answer template in an isolated temp dir
    //returns a result instead of throwing so remaining questions can still be processed
    public ValidationResult validate(QuestionSpec spec, String testerSource, int expectedCases) {
        if (expectedCases <= 0) return ValidationResult.failed(Stage.PARSE, "positive marks are required");
        try (var workspaceOwner = new TemporaryDirectory("autograder-gen-validate-");
                var classesOwner = new TemporaryDirectory("autograder-gen-classes-")) {
            Path workspace = workspaceOwner.path();
            Path classes = classesOwner.path();
            //place the student answer template & the generated tester side by side
            Files.writeString(workspace.resolve(spec.getId() + ".java"), spec.getAnswerTemplateSource());
            Files.writeString(workspace.resolve(spec.getTesterFileName()), testerSource);

            //copy the question's support files (e.g. DataException.class, persons.txt) like the real grader
            copySupportFiles(spec, workspace);

            //1 > compile both together > reject on compile failure or timeout
            ProcessResult compile = JavaTools.compile(workspace, classes, compilationTimeout);
            if (compile.isTimedOut()) {
                return ValidationResult.failed(Stage.COMPILE, "compilation timed out");
            }
            if (!compile.isSuccessful()) {
                return ValidationResult.failed(Stage.COMPILE, truncate(compile.getErrorOutput()));
            }

            //A generated tester must finish; partial student grading is a separate concern.
            ProcessResult run = JavaTools.run(workspace, classes, spec.getTesterMainClass(), executionTimeout);
            if (run.isTimedOut()) return ValidationResult.failed(Stage.RUN, "tester execution timed out");
            if (!run.isSuccessful()) {
                return ValidationResult.failed(Stage.RUN, truncate(run.getErrorOutput()));
            }

            //Parse complete cases and require the declared count and a consistent final score.
            List<TestCaseResult> cases;
            try {
                String output = run.getStandardOutput();
                //The grader's permissive parser ignores outcomes before the first header.
                //Generation must reject those orphan outcomes instead.
                boolean headerSeen = false;
                for (String line : output.lines().map(String::strip).toList()) {
                    if (line.matches("Test [^:]+:.*")) headerSeen = true;
                    if (!headerSeen && (line.equalsIgnoreCase("Passed") || line.equalsIgnoreCase("Failed")
                            || line.startsWith("Failed -> "))) {
                        return ValidationResult.failed(Stage.PARSE, "outcome appeared before a test header");
                    }
                }
                cases = parser.parse(output);
                if (cases.size() != expectedCases) {
                    return ValidationResult.failed(Stage.PARSE, "expected " + expectedCases + " cases but found " + cases.size());
                }
                List<String> lines = output.strip().lines().toList();
                double total = Double.parseDouble(lines.getLast().strip());
                long passed = cases.stream().filter(testCase -> testCase.getEarnedMarks() == 1).count();
                if (!Double.isFinite(total) || total != passed) {
                    return ValidationResult.failed(Stage.PARSE, "final score must equal passed-case count " + passed);
                }
            } catch (RuntimeException error) {
                return ValidationResult.failed(Stage.PARSE, error.getMessage());
            }

            return ValidationResult.ok(cases.size());
        } catch (IOException | RuntimeException error) {
            return ValidationResult.failed(Stage.COMPILE, "could not prepare or validate tester > " + error.getMessage());
        }
    }

    //copy the question's declared support files into the workspace > skip the answer template itself
    private void copySupportFiles(QuestionSpec spec, Path workspace) throws IOException {
        Path resourceDir = spec.getResourceDirectory();
        if (resourceDir == null) {
            return;
        }
        String templateName = spec.getId() + ".java"; //written from source already > never overwrite it
        for (String name : spec.getSupportFiles()) {
            if (name.equals(templateName)) {
                continue;
            }
            Path source = resourceDir.resolve(name);
            if (!Files.isRegularFile(source)) {
                throw new IOException("missing support file > " + name);
            }
            Path destination = workspace.resolve(name);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            FileUtils.copyFileReplacing(source, destination);
        }
    }

    //keep diagnostics readable in the per-question summary
    private static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return "(no output)";
        }
        String trimmed = text.strip();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) + "..." : trimmed;
    }
}
