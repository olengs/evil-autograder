package utils.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;

import config.Config;

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
    private final LinkedHashMap<String, String> jsonMap;

    private final ArrayList<String> logs;
    // Startup defaults allow logging before the UI has a valid configuration.
    private int bufferThreshold = Config.DEFAULT_LOG_BUFFER_THRESHOLD;

    private volatile EnumSet<Status> flags = EnumSet.allOf(Status.class);

    private JsonLineLogger() {
        hasInit = false;
        logs = new ArrayList<>();
        jsonMap = new LinkedHashMap<>();
    }

    public static JsonLineLogger getInstance() {
        if (instance == null) {
            instance = new JsonLineLogger();
        }

        return instance;
    }

    public void Init(Path logFile, int threshold) throws IOException {
        if (hasInit) return;

        if (threshold <= 0 || threshold > Integer.MAX_VALUE - 5) {
            throw new IllegalArgumentException("log buffer threshold must be positive and allow threshold + 5 without overflow");
        }
        
        logs.ensureCapacity(threshold + 5);
        bufferThreshold = threshold;
        
        Path target = logFile.toAbsolutePath().normalize();
        FileUtils.forceMkdirParent(target.toFile());
        FileUtils.writeStringToFile(target.toFile(), "", StandardCharsets.UTF_8);
        logfilePath = target;
        hasInit = true;
    }

    /// param flags to enable which log status to be enabled, by default all types of logs are enabled.
    public void Init(Path logFile, int threshold, EnumSet<Status> flags) throws IOException {
        if (hasInit) return;
        setLevels(flags);
        Init(logFile, threshold);
    }

    public void setLevels(EnumSet<Status> levels) {
        flags = levels.clone();
    }

    public void append(Status status, String feature, String message) {
        if (!hasInit) throw new RuntimeException("using Json Logger without initialization");
        if (!flags.contains(status)) return;

        synchronized (logs) {
            jsonMap.put("status", status.toString());
            jsonMap.put("message", String.format("[%s]: %s", feature, message));
            logs.add(gson.toJson(jsonMap));
            jsonMap.clear();
            if (logs.size() >= bufferThreshold) {
                try {
                    flush();
                } catch (IOException e) {
                    System.err.println("Log write failed; entries remain buffered: " + e.getMessage());
                }
            }
        }
    }

    // Flush handled failures immediately so details are available while the UI stays open.
    public void logException(Status status, String feature, String context, Throwable error) {
        if (!flags.contains(status)) return;
        var text = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(text));
        String detail = context + "\n" + text;
        if (!hasInit) {
            System.err.println("[" + feature + "] " + detail);
            return;
        }
        append(status, feature, detail);
    }

    public void flush() throws IOException {
        if (!hasInit) throw new RuntimeException("using Json Logger without initialization");
        synchronized (logs) {
            FileUtils.writeLines(logfilePath.toFile(), StandardCharsets.UTF_8.name(), logs, true);
            logs.clear();
        }
    }
}
