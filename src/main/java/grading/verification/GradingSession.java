package grading.verification;

import config.Config;
import grading.QuestionGrade;
import grading.progress.GradingProgress;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import submission.StudentSubmission;

public final class GradingSession implements ParameterResolver { //shares configuration, progress & results across JUnit checks
    static final String sessionParameter = "autograder.session";
    private static final Map<String, Input> sessions = new ConcurrentHashMap<>();

    public static final class Input {
        private final Config config;
        private final GradingProgress progress;
        private final boolean printResults;
        private volatile Map<StudentSubmission, List<QuestionGrade>> results;

        private Input(Config config, GradingProgress progress, boolean printResults, Map<StudentSubmission, List<QuestionGrade>> results) {
            this.config = config;
            this.progress = progress;
            this.printResults = printResults;
            this.results = results;
        }

        public Config config() { return config; }
        public GradingProgress progress() { return progress; }
        public boolean printResults() { return printResults; }
        public Map<StudentSubmission, List<QuestionGrade>> results() { return results; }

        synchronized void complete(Map<StudentSubmission, List<QuestionGrade>> results) {
            if (this.results != null) throw new IllegalStateException("grading session already has results");
            this.results = Objects.requireNonNull(results);
        }
    }

    static String register(Config config, Map<StudentSubmission, List<QuestionGrade>> results) {
        return register(new Input(config, new GradingProgress(), false, Objects.requireNonNull(results)));
    }

    static String register(Config config, GradingProgress progress) {
        return register(new Input(config, progress, false, null));
    }

    private static String register(Input input) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, input);
        return id;
    }

    static Input input(String id) {
        Input input = sessions.get(id);
        if (input == null) throw new ParameterResolutionException("grading session has already closed");
        return input;
    }

    static void release(String id) {
        sessions.remove(id);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Input.class;
    }

    @Override
    public Input resolveParameter(ParameterContext parameter, ExtensionContext context) {
        var id = context.getConfigurationParameter(sessionParameter);
        if (id.isPresent()) {
            return input(id.get());
        }
        // Standalone Maven/IDE execution also supports -Dautograder.config=path.
        String configFile = context.getConfigurationParameter("autograder.config").orElse("config.properties");
        try {
            return new Input(new Config(Path.of(configFile)), new GradingProgress(), true, null);
        } catch (IOException | RuntimeException failure) {
            throw new ParameterResolutionException("cannot load grading configuration > " + configFile, failure);
        }
    }
}
