package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

public class Config {

    private final Properties props = new Properties(); //@1ejun6 > specialised collection used to manage application settings & configuration data
    //@1ejun6 > extends as a hashtable class & provides convenient methods for reading & storing & updating property values from external files

    public Config() throws IOException { //@1ejun6 > ioexception is passed to caller if file cannot be read > checked
        this(Path.of("config.properties"));
    }

    public Config(Path configFile) throws IOException {
        try (
            Reader reader = Files.newBufferedReader(configFile)) {
            props.load(reader); //@1ejun6 > reads files key=value pairs into props
        }

        getInputDirectory();
        getOutputDirectory();
        getSubmissionsDirectory();
        getTestersDirectory();
        getResourcesDirectory();
        getScoresheetFile();
        getCompilationTimeout();
        getExecutionTimeout();

        // settings for LLM tester generator
        getExamPdfFile();
        getGenerationOutputDirectory();
        getLlmProvider();
        getLlmModel();
        getLlmApiKeyEnv();
        isLlmSendPdf();
        getGenerationMaxRetries();
    }

    public Path getInputDirectory() {
        return Path.of(getRequired("input.directory")).toAbsolutePath().normalize();
    }

    public Path getOutputDirectory() {
        return Path.of(getRequired("output.directory")).toAbsolutePath().normalize();
    }

    public Path getSubmissionsDirectory() {
        return getInputDirectory().resolve(getRequired("submissions.folder"));
    }

    public Path getTestersDirectory() {
        return getInputDirectory().resolve(getRequired("testers.folder"));
    }

    public Path getResourcesDirectory() {
        return getInputDirectory().resolve(getRequired("resources.folder"));
    }

    public Path getScoresheetFile() {
        return getInputDirectory().resolve(getRequired("scoresheet.file"));
    }

    public Duration getCompilationTimeout() {
        return getTimeout("compilation.timeout.seconds");
    }

    public Duration getExecutionTimeout() {
        return getTimeout("execution.timeout.seconds");
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

    public String getLlmProvider() {
        return getRequired("llm.provider").toLowerCase(java.util.Locale.ROOT);
    }

    public String getLlmModel() {
        return getRequired("llm.model");
    }

    // base url optional > required only for openai-compatible & ollama providers
    public String getLlmBaseUrl() {
        return getOptional("llm.base.url", "");
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

    private Duration getTimeout(String key) {
        long seconds; //@1ejun6 > java automatically converts the int to a long

        try {
            seconds = Long.parseLong(getRequired(key));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be a positive whole number", error); //@1ejun6 > unchecked
        }

        if (seconds <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero"); //@1ejun6 > unchecked
        }

        return Duration.ofSeconds(seconds);
    }
}