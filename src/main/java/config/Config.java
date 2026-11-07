package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

        //settings for llm tester generator
        getExamPdfFile();
        getGenerationOutputDirectory();
        getLlmModel();
        getLlmApiKeyEnv();
        isLlmSendPdf();
        getLlmRequestTimeout();
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

    //llm tester generation settings
    public Path getExamPdfFile() {
        return getInputDirectory().resolve(getRequired("exam.pdf.file"));
    }

    public Path getGenerationOutputDirectory() {
        return getInputDirectory().resolve(getRequired("generation.output.dir"));
    }

    public String getLlmModel() {
        return getRequired("llm.model");
    }

    public String getLlmApiKeyEnv() {
        return getRequired("llm.api.key.env");
    }

    public Duration getLlmRequestTimeout() {
        return getTimeout("llm.request.timeout.seconds");
    }

    public boolean isLlmSendPdf() {
        return getBoolean("llm.send.pdf");
    }

    private String getRequired(String key) {
        String value = props.getProperty(key); //@1ejun6 > finds value belonging to this key

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("config > missing setting >" + key);
        }

        return value.strip();
    }

    //required boolean > only true & false accepted
    private boolean getBoolean(String key) {
        String value = getRequired(key).toLowerCase(java.util.Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return value.equals("true");
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