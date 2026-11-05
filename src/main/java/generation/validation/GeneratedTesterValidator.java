package generation.validation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import generation.questionSpec.QuestionSpec;
import generation.validation.ValidationResult.Stage;
import grading.TesterOutputParser;
import model.ProcessResult;
import model.TestCaseResult;
import utils.FileUtils;
import utils.JavaProcessUtils;
import utils.ProcessExecutor;

public class GeneratedTesterValidator { //checks a generated tester actually compiles, runs & parses against its template
    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final TesterOutputParser parser = new TesterOutputParser();

    public GeneratedTesterValidator(Duration compilationTimeout, Duration executionTimeout) {
        this.compilationTimeout = compilationTimeout;
        this.executionTimeout = executionTimeout;
    }

    // validate one generated tester against the question's answer template in an isolated temp dir
    // returns a result instead of throwing so the caller can retry or skip
    public ValidationResult validate(QuestionSpec spec, String testerSource) {
        Path workspace;
        try {
            workspace = Files.createTempDirectory("autograder-gen-validate-");
        } catch (IOException error) {
            return ValidationResult.failed(Stage.COMPILE, "could not create temp workspace > " + error.getMessage());
        }

        try {
            // place the student answer template & the generated tester side by side
            Files.writeString(workspace.resolve(spec.getId() + ".java"), spec.getAnswerTemplateSource());
            Files.writeString(workspace.resolve(spec.getTesterFileName()), testerSource);

            // copy the question's support files (e.g. DataException.class, persons.txt) like the real grader
            copySupportFiles(spec, workspace);

            // 1 > compile both together > reject on compile failure or timeout
            ProcessResult compile = compile(workspace);
            if (compile.isTimedOut()) {
                return ValidationResult.failed(Stage.COMPILE, "compilation timed out");
            }
            if (!compile.isSuccessful()) {
                return ValidationResult.failed(Stage.COMPILE, truncate(compile.getErrorOutput()));
            }

            // 2 > run the tester main class > a timeout here still lets us parse completed cases
            ProcessResult run = executeTester(workspace, spec.getTesterMainClass());
            if (!run.isTimedOut() && !run.isSuccessful()) {
                return ValidationResult.failed(Stage.RUN, truncate(run.getErrorOutput()));
            }

            // 3 > parse output the same way the grader will > must yield at least one case
            List<TestCaseResult> cases;
            try {
                cases = run.isTimedOut()
                        ? parser.parseCompleted(run.getStandardOutput())
                        : parser.parse(run.getStandardOutput());
            } catch (RuntimeException error) {
                return ValidationResult.failed(Stage.PARSE, error.getMessage());
            }
            if (cases.isEmpty()) {
                return ValidationResult.failed(Stage.PARSE, "tester produced no parseable test cases");
            }

            return ValidationResult.ok(cases.size());
        } catch (IOException error) {
            return ValidationResult.failed(Stage.COMPILE, "could not write sources > " + error.getMessage());
        } finally {
            // always clean up the throwaway workspace
            try {
                FileUtils.deleteDirectory(workspace);
            } catch (IOException ignored) {
                // cleanup failure must not mask the validation result
            }
        }
    }

    // copy the question's declared support files into the workspace > skip the answer template itself
    private void copySupportFiles(QuestionSpec spec, Path workspace) throws IOException {
        Path resourceDir = spec.getResourceDirectory();
        if (resourceDir == null) {
            return;
        }
        String templateName = spec.getId() + ".java"; // written from source already > never overwrite it
        for (String name : spec.getSupportFiles()) {
            if (name.equals(templateName)) {
                continue;
            }
            Path source = resourceDir.resolve(name);
            if (!Files.isRegularFile(source)) {
                continue; // missing support file > let compilation surface the real error
            }
            Path destination = workspace.resolve(name);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            FileUtils.copyFile(source, destination, FileUtils.CopyOptions.REPLACE_EXISTING);
        }
    }

    // compile every .java in the workspace into a classes folder > mirrors QuestionGrader
    private ProcessResult compile(Path workspace) throws IOException {
        Path classes = Files.createDirectories(workspace.resolve("classes"));
        List<String> command = new ArrayList<>();
        command.add(JavaProcessUtils.getCompilerPath().toString());
        command.add("-d");
        command.add(classes.toString());
        command.add("-cp");
        command.add(workspace.toString());

        List<Path> sources;
        try (var files = Files.list(workspace)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IOException("no java sources in " + workspace);
        }
        for (Path source : sources) {
            command.add(source.toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile());
        return ProcessExecutor.run(builder, compilationTimeout, "prc-001", "javac could not be started");
    }

    // run the tester main class with the compiled classes on the classpath
    private ProcessResult executeTester(Path workspace, String mainClass) {
        String classpath = workspace.resolve("classes") + File.pathSeparator + workspace;
        ProcessBuilder builder = new ProcessBuilder(
                JavaProcessUtils.getRuntimePath().toString(), "-cp", classpath, mainClass);
        builder.directory(workspace.toFile());
        return ProcessExecutor.run(builder, executionTimeout, "prc-002", "java execution failed");
    }

    // keep error output short so it fits a retry prompt without flooding it
    private static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return "(no output)";
        }
        String trimmed = text.strip();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) + "..." : trimmed;
    }
}
