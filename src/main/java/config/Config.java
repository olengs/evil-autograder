package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.Map;

public class Config { //loads grading paths & process time limits from configuration
    private final Properties props;
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
        props = new Properties();
        Properties properties = props;
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

    // LLM tester generation settings

    public Path getExamPdfFile() {
        return getInputDirectory().resolve(getRequired("exam.pdf.file"));
    }

    public Path getGenerationOutputDirectory() {
        return getInputDirectory().resolve(getRequired("generation.output.dir"));
    }

    // folder of example testers shown to the llm > optional > empty when blank so no exemplar is sent
    public Optional<Path> getExemplarDirectory() {
        String folder = getOptional("exemplar.folder", "");
        return folder.isEmpty() ? Optional.empty() : Optional.of(getInputDirectory().resolve(folder));
    }

    public String getLlmModel() {
        return getRequired("llm.model");
    }

    public String getLlmApiKeyEnv() {
        return getRequired("llm.api.key.env");
    }

    public boolean isLlmSendPdf() {
        return getBoolean("llm.send.pdf");
    }

    public int getGenerationMaxRetries() {
        return getNonNegativeInt("generation.max.retries");
    }

    // toggle for one named add-on prompter > default off when key absent
    public boolean isPrompterEnabled(String prompterId) {
        return getOptionalBoolean("prompter." + prompterId + ".enabled", false);
    }

    private String getRequired(String key) {
        String value = props.getProperty(key); //@1ejun6 > finds value belonging to this key

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("config > missing setting >" + key);
        }

        return value.strip();
    }

    // optional setting > fall back to default when key missing or blank
    private String getOptional(String key, String fallback) {
        String value = props.getProperty(key);
        return (value == null || value.isBlank()) ? fallback : value.strip();
    }

    // required boolean > only true & false accepted
    private boolean getBoolean(String key) {
        String value = getRequired(key).toLowerCase(java.util.Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return value.equals("true");
    }

    // optional boolean > fall back when key missing or blank
    private boolean getOptionalBoolean(String key, boolean fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalised = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (!normalised.equals("true") && !normalised.equals("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return normalised.equals("true");
    }

    // whole number zero or greater > used for retry count
    private int getNonNegativeInt(String key) {
        int number;
        try {
            number = Integer.parseInt(getRequired(key));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be a whole number", error);
        }
        if (number < 0) {
            throw new IllegalArgumentException(key + " must be zero or greater");
        }
        return number;
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
