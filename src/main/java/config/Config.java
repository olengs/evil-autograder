package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.Map;

public class Config { //loads grading paths & process time limits from configuration
    private final Properties properties;
    private final Path inputDirectory;
    private final Path outputDirectory;
    private final Path submissionsDirectory;
    private final Path testersDirectory;
    private final Path resourcesDirectory;
    private final Path scoresheetFile;
    private final Duration compilationTimeout;
    private final Duration executionTimeout;

    public Config() throws IOException {
        this(Path.of("config.properties"));
    }

    public Config(Path configFile) throws IOException {
        this(configFile, Map.of());
    }

    public Config(Path configFile, Map<String, String> overrides) throws IOException {
        properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        }
        properties.putAll(overrides); //replacement paths apply to this run only

        //fail before grading starts if any required setting is missing or invalid
        inputDirectory = Path.of(getRequired(properties, "input.directory")).toAbsolutePath().normalize();
        outputDirectory = Path.of(getRequired(properties, "output.directory")).toAbsolutePath().normalize();
        submissionsDirectory = inputDirectory.resolve(getRequired(properties, "submissions.folder"));
        testersDirectory = inputDirectory.resolve(getRequired(properties, "testers.folder"));
        resourcesDirectory = inputDirectory.resolve(getRequired(properties, "resources.folder"));
        scoresheetFile = inputDirectory.resolve(getRequired(properties, "scoresheet.file"));
        compilationTimeout = getTimeout(properties, "compilation.timeout.seconds");
        executionTimeout = getTimeout(properties, "execution.timeout.seconds");
    }

    public Path getInputDirectory() {
        return inputDirectory;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public Path getSubmissionsDirectory() {
        return submissionsDirectory;
    }

    public Path getTestersDirectory() {
        return testersDirectory;
    }

    public Path getResourcesDirectory() {
        return resourcesDirectory;
    }

    public Path getScoresheetFile() {
        return scoresheetFile;
    }

    public Duration getCompilationTimeout() {
        return compilationTimeout;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    // Generation settings are read only when generating testers, so grading needs no API setup.
    public Path getExamPdfFile() {
        return inputDirectory.resolve(getRequired(properties, "exam.pdf.file"));
    }

    public Path getGenerationOutputDirectory() {
        return inputDirectory.resolve(getRequired(properties, "generation.output.dir"));
    }

    public String getLlmModel() {
        return getRequired(properties, "llm.model");
    }

    public String getLlmApiKeyEnv() {
        return getRequired(properties, "llm.api.key.env");
    }

    public Duration getLlmRequestTimeout() {
        return getTimeout(properties, "llm.request.timeout.seconds");
    }

    public boolean isLlmSendPdf() {
        String value = getRequired(properties, "llm.send.pdf");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("llm.send.pdf must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public void validateGenerationSettings() {
        getExamPdfFile();
        getGenerationOutputDirectory();
        getLlmModel();
        getLlmApiKeyEnv();
        getLlmRequestTimeout();
        isLlmSendPdf();
    }

    private static String getRequired(Properties properties, String key) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("config > missing setting >" + key);
        }

        return value.strip();
    }

    private static Duration getTimeout(Properties properties, String key) {
        long seconds;

        try {
            seconds = Long.parseLong(getRequired(properties, key));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be a positive whole number", error);
        }

        if (seconds <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }

        return Duration.ofSeconds(seconds);
    }
}
