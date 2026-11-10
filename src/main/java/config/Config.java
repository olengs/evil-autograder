package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.EnumSet;
import java.util.Locale;
import utils.logging.JsonLineLogger.Status;
import java.util.Properties;

public class Config { //loads grading paths & process time limits from configuration
    public static final int DEFAULT_LOG_BUFFER_THRESHOLD = 12;

    private final int logBufferThreshold;
    private final EnumSet<Status> logLevels;
    private final Properties properties;
    private final Path inputDirectory;
    private final Path outputDirectory;
    private final Path submissionsDirectory;
    private final Path testersDirectory;
    private final Path resourcesDirectory;
    private final Path scoresheetFile;
    private final Duration compilationTimeout;
    private final Duration executionTimeout;
    private final int gradingWorkers;
    private final int headerSearchDepth;
    private final int answerSearchDepth;
    private final long processOutputMaxBytes;

    public Config() throws IOException {
        this(Path.of("config.properties"));
    }

    public Config(Path configFile) throws IOException {
        this(configFile, Map.of());
    }

    public Config(Path configFile, Map<String, String> overrides) throws IOException {
        this(loadProperties(configFile), overrides);
    }

    // Copies settings for one operation without changing config.properties.
    private Config(Properties source, Map<String, String> overrides) {
        properties = new Properties();
        properties.putAll(source);
        properties.putAll(overrides);

        //fail before grading starts if any required setting is missing or invalid
        inputDirectory = Path.of(getRequired(properties, "input.directory")).toAbsolutePath().normalize();
        outputDirectory = Path.of(getRequired(properties, "output.directory")).toAbsolutePath().normalize();
        submissionsDirectory = inputDirectory.resolve(getRequired(properties, "submissions.folder"));
        testersDirectory = inputDirectory.resolve(getRequired(properties, "testers.folder"));
        resourcesDirectory = inputDirectory.resolve(getRequired(properties, "resources.folder"));
        scoresheetFile = inputDirectory.resolve(getRequired(properties, "scoresheet.file"));
        compilationTimeout = getTimeout(properties, "compilation.timeout.seconds");
        executionTimeout = getTimeout(properties, "execution.timeout.seconds");
        int gradingWorkersValue = Integer.parseInt(getRequired(properties, "grading.workers"));
        gradingWorkers = gradingWorkersValue < 1 ? 1 : gradingWorkersValue;
        headerSearchDepth = getSearchDepth(properties, "submission.header.search.depth", 6);
        answerSearchDepth = getSearchDepth(properties, "submission.answer.search.depth", 5);
        int logBufferThreshold = Integer.parseInt(properties.getProperty("log.buffer.threshold", "12").strip());
        this.logBufferThreshold = logBufferThreshold < 12 ? 12 : logBufferThreshold;
        logLevels = EnumSet.noneOf(Status.class);

        String configuredLevels = properties.getProperty("log.levels", "LOG,WARNING,ERROR").strip();
        if (!configuredLevels.isEmpty()) {
            for (String level : configuredLevels.split(",", -1)) {
                try {
                    logLevels.add(Status.valueOf(level.strip().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("log.levels accepts LOG, WARNING, ERROR; invalid value > " + level, error);
                }
            }
        }
        
        // Validate output limits before any worker starts.
        Long processOutMaxBytes = Long.parseLong(properties.getProperty("process.output.max.MiB", "2"));
        // << 20 to convert to MiB
        this.processOutputMaxBytes = (processOutMaxBytes < 1 ? 1 : processOutMaxBytes) << 20;
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) { values.load(reader); }
        return values;
    }

    public EnumSet<Status> getLogLevels() {
        return logLevels.clone();
    }

    public int getLogBufferThreshold() {
        return logBufferThreshold;
    }

    public long getProcessOutputMaxBytes() {
        return processOutputMaxBytes;
    }


    public Config withOverrides(Map<String, String> overrides) {
        return new Config(properties, overrides);
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

    public int getHeaderSearchDepth() {
        return headerSearchDepth;
    }

    public int getAnswerSearchDepth() {
        return answerSearchDepth;
    }

    public int getNumGradingWorkers() {
    return gradingWorkers;
}

    // Generation settings are read only when generating testers, so grading needs no API setup.
    public Path getExamPdfFile() {
        return inputDirectory.resolve(getRequired(properties, "exam.pdf.file"));
    }

    public Path getGenerationOutputDirectory() {
        String key = properties.containsKey("generation.files.dir") ? "generation.files.dir" : "generation.output.dir";
        return inputDirectory.resolve(getRequired(properties, key));
    }

    public String getLlmModel() {
        return getRequired(properties, "llm.model");
    }

    public String getLlmReasoningEffort() {
        return getRequired(properties, "llm.reasoning.effort");
    }

    public String getLlmApiKeyEnv() {
        return getRequired(properties, "llm.api.key.env");
    }

    public Duration getLlmRequestTimeout() {
        return getTimeout(properties, "llm.request.timeout.seconds");
    }

    public void validateGenerationSettings() {
        getExamPdfFile();
        getGenerationOutputDirectory();
        getLlmModel();
        getLlmReasoningEffort();
        getLlmApiKeyEnv();
        getLlmRequestTimeout();
    }

    private static String getRequired(Properties properties, String key) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("config > missing setting >" + key);
        }

        return value.strip();
    }

    private static int getSearchDepth(Properties properties, String key, int defaultValue) {
        try {
            int depth = Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)).strip());
            if (depth > 0) return depth;
        } catch (NumberFormatException ignored) {
            // Report the setting name for malformed values as well as nonpositive depths.
        }
        throw new IllegalArgumentException(key + " must be a positive whole number");
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
