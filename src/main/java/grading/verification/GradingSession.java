//supplies junit with configuration & optional existing grading results for one run
//shares results across console & parallel ui checks without grading submissions again

package grading.verification;

import config.Config;
import grading.QuestionGrade;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import submission.StudentSubmission;

public final class GradingSession implements ParameterResolver {
    static final String sessionParameter = "autograder.session";
    private static final Map<String, Input> sessions = new ConcurrentHashMap<>();

    public record Input(Config config, Map<StudentSubmission, List<QuestionGrade>> results) {}

    static String register(Config config, Map<StudentSubmission, List<QuestionGrade>> results) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Input(config, results));
        return id;
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
            Input input = sessions.get(id.get());
            if (input == null) throw new ParameterResolutionException("grading session has already closed");
            return input;
        }
        // Standalone Maven/IDE execution also supports -Dautograder.config=path.
        String configFile = context.getConfigurationParameter("autograder.config").orElse("config.properties");
        try {
            return new Input(new Config(Path.of(configFile)), null);
        } catch (IOException | RuntimeException failure) {
            throw new ParameterResolutionException("cannot load grading configuration > " + configFile, failure);
        }
    }
}
