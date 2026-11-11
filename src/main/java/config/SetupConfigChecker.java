package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import utils.logging.JsonLineLogger;

//checks setup config paths & accepts replacement paths for this run
public final class SetupConfigChecker { 
    private Path configFile;
    private final Map<String, String> overrides = new HashMap<>();
    private Config config;
    private Issue issue;

    public record Issue(String setting, String label, Path path, String reason) {
        public String message() {
            return label + " > " + reason + " > " + path;
        }
    }

    public SetupConfigChecker(Path configFile) {
        this.configFile = configFile.toAbsolutePath().normalize();
        check();
    }

    public Issue issue() {
        return issue;
    }

    public Config config() throws IOException {
        if (issue != null) throw new IOException(issue.message());
        return config;
    }

    public void replace(String input) throws IOException {
        if (issue == null) throw new IllegalStateException("setup is already valid");
        Issue previous = issue;
        String value = input.strip();
        if (value.isEmpty()) { check(); return; } // Enter retries after editing the configuration.
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
            JsonLineLogger.getInstance().logException(JsonLineLogger.Status.ERROR,
                    "SetupConfigChecker", "cannot load configuration > " + configFile, error);
            String reason = error.getMessage();
            if (reason == null || reason.isBlank() || reason.length() > 80) reason = "Invalid configuration; see log";
            issue = new Issue("config.file", "config file", configFile, reason);
            return;
        }
        issue = input(config.getInputDirectory(), "input.directory", "input folder", false);
        if (issue == null) issue = input(config.getResourcesDirectory(), "resources.folder", "resources folder", false);
        // Submission, tester, and output paths are checked for the selected operation.
    }

    private Issue input(Path path, String setting, String label, boolean file) {
        path = path.toAbsolutePath().normalize();
        String reason = !Files.exists(path) ? "not found" : (file ? !Files.isRegularFile(path) : !Files.isDirectory(path)) ? (file ? "not a regular file" : "not a directory") : !Files.isReadable(path) ? "not readable" : null;
        if (reason != null) JsonLineLogger.getInstance().logException(
                JsonLineLogger.Status.ERROR, "SetupConfigChecker", "invalid setting > " + setting,
                new IOException(reason + " > " + path));
        return reason == null ? null : new Issue(setting, label, path, reason);
    }
}
