package utils.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import java.util.ArrayList;
import config.Config;
import java.util.EnumSet;
import java.util.Map;

import com.google.gson.Gson;

// appends structured log entries as JSON lines
// Shared by grading workers; buffer operations are synchronized.
public final class JsonLineLogger { 

    public enum Status {
        LOG,
        WARNING,
        ERROR,
    }

    private final Gson gson = new Gson();
    private static JsonLineLogger instance;
    private Path logfilePath;
    private boolean hasInit;

    private final ArrayList<String> logs;
    // Startup defaults allow logging before the UI has a valid configuration.
    private int bufferThreshold = Config.DEFAULT_LOG_BUFFER_THRESHOLD;

    private EnumSet<Status> flags = EnumSet.allOf(Status.class);

    private JsonLineLogger() {
        hasInit = false;
        logs = new ArrayList<>();
    }

    public static JsonLineLogger getInstance() {
        if (instance == null) {
            instance = new JsonLineLogger();
        }

        return instance;
    }

    public void Init(Path logFile, int threshold) throws IOException {
        if (hasInit) return;
        this.logfilePath = logFile;
        if (threshold <= 0 || threshold > Integer.MAX_VALUE - 5) {
            throw new IllegalArgumentException("log buffer threshold must be positive and allow threshold + 5 without overflow");
        }
        logs.ensureCapacity(threshold + 5);
        bufferThreshold = threshold;
        // Creates parent folders and truncates the previous log for this run.
        FileUtils.writeStringToFile(logfilePath.toFile(), "", StandardCharsets.UTF_8);
        hasInit = true;
    }

    /// param flags to enable which log status to be enabled, by default all types of logs are enabled.
    public void Init(Path logFile, int threshold, EnumSet<Status> flags) throws IOException {
        if (hasInit) return;
        this.flags = flags;
        Init(logFile, threshold);
    }

    public void append(Status status, String feature, String message) {
        if (!hasInit) throw new RuntimeException("using Json Logger without initialization");
        if (!flags.contains(status)) return;

        synchronized (logs) {
            logs.add(gson.toJson(Map.of("status", status.toString(), "message", String.format("[%s]: %s", feature, message))));
            if (logs.size() >= bufferThreshold) {
                try {
                    flush();
                } catch (IOException e) {
                    System.err.println("Log write failed; entries remain buffered: " + e.getMessage());
                }
            }
        }
    }

    public void flush() throws IOException {
        if (!hasInit) throw new RuntimeException("using Json Logger without initialization");
        synchronized (logs) {
            FileUtils.writeLines(logfilePath.toFile(), StandardCharsets.UTF_8.name(), logs, true);
            logs.clear();
        }
    }
}
