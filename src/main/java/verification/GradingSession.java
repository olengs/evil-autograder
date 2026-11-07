package verification;

import config.Config;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import progress.GradingProgress;

/** Supplies each JUnit run with its own config and progress listener. */
public final class GradingSession implements ParameterResolver {
    static final String SESSION_PARAMETER = "autograder.session";
    private static final Map<String, Input> SESSIONS = new ConcurrentHashMap<>();

    public record Input(Config config, GradingProgress progress, boolean printResults) {}

    static String register(Config config, GradingProgress progress, boolean printResults) {
        String id = UUID.randomUUID().toString();
        SESSIONS.put(id, new Input(config, progress, printResults));
        return id;
    }

    static void release(String id) {
        SESSIONS.remove(id);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Input.class;
    }

    @Override
    public Input resolveParameter(ParameterContext parameter, ExtensionContext context) {
        var id = context.getConfigurationParameter(SESSION_PARAMETER);
        if (id.isPresent()) {
            Input input = SESSIONS.get(id.get());
            if (input == null) throw new ParameterResolutionException("grading session has already closed");
            return input;
        }
        // Standalone Maven/IDE execution also supports -Dautograder.config=path.
        String configFile = context.getConfigurationParameter("autograder.config").orElse("config.properties");
        try {
            return new Input(new Config(Path.of(configFile)), new GradingProgress(), true);
        } catch (IOException | RuntimeException failure) {
            throw new ParameterResolutionException("cannot load grading configuration > " + configFile, failure);
        }
    }
}
