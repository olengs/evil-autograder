package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import utils.files.PdfFileUtils;

public final class SetupConfig { //checks setup paths & accepts replacement paths for this run
    public enum TesterSource { existing, generated }

    private Path configFile;
    private final Map<String, String> overrides = new HashMap<>();
    private Config config;
    private Issue issue;
    private TesterSource testerSource = TesterSource.existing;

    public record Issue(String setting, String label, Path path, String reason) {
        public String message() {
            return label + " > " + reason + " > " + path;
        }
    }

    public SetupConfig(Path configFile) {
        this.configFile = configFile.toAbsolutePath().normalize();
        check();
    }

    public Issue issue() {
        return issue;
    }

    public TesterSource testerSource() {
        return testerSource;
    }

    public void selectSource(TesterSource source) {
        testerSource = source;
        check();
    }

    public void generationPaths(String examPdf, String outputDirectory) throws IOException {
        try {
            if (config().isLlmSendPdf() && examPdf.isBlank()) throw new IllegalArgumentException("exam PDF is required");
            String exam = Path.of(examPdf.strip()).toAbsolutePath().normalize().toString();
            if (outputDirectory.isBlank()) throw new IllegalArgumentException("output folder is required");
            String output = Path.of(outputDirectory.strip()).toAbsolutePath().normalize().toString();
            overrides.put("exam.pdf.file", exam);
            overrides.put("generation.files.dir", output);
            overrides.put("generation.output.dir", output);
        } catch (IllegalArgumentException error) {
            throw new IOException("invalid generation path > " + error.getMessage(), error);
        }
        check();
    }

    public Config configForGrading() throws IOException {
        Config current = config();
        if (testerSource == TesterSource.existing) return current;
        Map<String, String> gradingOverrides = new HashMap<>(overrides);
        gradingOverrides.put("testers.folder", current.getGenerationOutputDirectory().toString());
        return new Config(configFile, gradingOverrides);
    }

    public Config config() throws IOException {
        if (issue != null) throw new IOException(issue.message());
        return config;
    }

    public void replace(String input) throws IOException {
        if (issue == null) throw new IllegalStateException("setup is already valid");
        Issue previous = issue;
        String value = input.strip();
        if (value.isEmpty()) throw new IOException("setup stopped > no replacement path > " + previous.message());
        Path replacement;
        try {
            replacement = Path.of(value).toAbsolutePath().normalize();
        } catch (IllegalArgumentException error) {
            throw new IOException("invalid replacement path > " + error.getMessage(), error);
        }
        if (previous.setting().equals("config.file")) {
            configFile = replacement;
            overrides.clear();
        } else overrides.put(previous.setting(), replacement.toString());
        check();
    }

    private void check() {
        config = null;
        issue = input(configFile, "config.file", "config file", true);
        if (issue != null) return;
        try {
            config = new Config(configFile, overrides);
        } catch (IOException | IllegalArgumentException error) {
            issue = new Issue("config.file", "config file", configFile, error.getMessage());
            return;
        }
        issue = input(config.getInputDirectory(), "input.directory", "input folder", false);
        if (issue == null) issue = input(config.getSubmissionsDirectory(), "submissions.folder", "submissions folder", false);
        if (issue == null && testerSource == TesterSource.existing) {
            issue = input(config.getTestersDirectory(), "testers.folder", "testers folder", false);
        }
        if (issue == null) issue = input(config.getResourcesDirectory(), "resources.folder", "resources folder", false);
        if (issue == null) {
            Path scoresheet = config.getScoresheetFile();
            issue = Files.exists(scoresheet, LinkOption.NOFOLLOW_LINKS)
                    ? input(scoresheet, "scoresheet.file", "student csv", true)
                    : output(scoresheet, "scoresheet.file", "student csv");
        }
        if (issue == null) issue = output(config.getOutputDirectory(), "output.directory", "output folder");
        if (issue == null && testerSource == TesterSource.generated) checkGeneration();
    }

    private void checkGeneration() {
        try {
            config.validateGenerationSettings();
        } catch (IllegalArgumentException error) {
            issue = new Issue("config.file", "generation settings", configFile, error.getMessage());
            return;
        }
        if (config.isLlmSendPdf()) {
            try { PdfFileUtils.requirePdf(config.getExamPdfFile()); }
            catch (IOException error) {
                issue = new Issue("exam.pdf.file", "exam PDF", config.getExamPdfFile(), error.getMessage());
            }
        }
        if (issue == null) {
            issue = output(config.getGenerationOutputDirectory(), "generation.files.dir", "generated testers folder");
        }
    }

    private Issue input(Path path, String setting, String label, boolean file) {
        path = path.toAbsolutePath().normalize();
        String reason = !Files.exists(path) ? "not found" : (file ? !Files.isRegularFile(path) : !Files.isDirectory(path)) ? (file ? "not a regular file" : "not a directory") : !Files.isReadable(path) ? "not readable" : null;
        return reason == null ? null : new Issue(setting, label, path, reason);
    }

    private Issue output(Path path, String setting, String label) {
        path = path.toAbsolutePath().normalize();
        Path parent = path;
        while (parent != null && !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) parent = parent.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) return new Issue(setting, label, path, "no writable directory at this path or its existing parents");
        return null;
    }
}
