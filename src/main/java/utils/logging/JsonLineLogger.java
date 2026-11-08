package utils.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

// appends structured log entries as JSON lines
// done as singleton, should only run on main thread.
public final class JsonLineLogger { 
    private final Gson gson = new Gson();
    private static JsonLineLogger instance;
    private Path logfilePath;
    private boolean hasInit;

    private final List<String> logs;
    // TODO: MOVE STATIC FINAL VARS TO CONFIG
    private static final int MAX_CAPACITY = 15;
    private static final int CAPACITY_THRESHOLD = 12;

    private JsonLineLogger() {
        hasInit = false;
        logs = Collections.synchronizedList(new ArrayList<>(MAX_CAPACITY));
    }

    public static JsonLineLogger getInstance() {
        if (instance == null) {
            instance = new JsonLineLogger();
        }

        return instance;
    }

    public void Init(Path logFile) throws IOException {
        this.logfilePath = logFile;
        Path parent = logFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        //CLEAR the old logs
        Files.writeString(logfilePath, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        hasInit = true;
    }

    public void append(Map<String, ?> fields) throws IOException {
        if (!hasInit) throw new RuntimeException("using Json Logger without initialization");
        logs.add(gson.toJson(fields));
        if (logs.size() >= CAPACITY_THRESHOLD) flush();
    }

    public void flush() throws IOException {
        if (!hasInit) throw new RuntimeException("using Json Logger without initialization");
        synchronized (logs) {
            Files.write(logfilePath, logs, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        logs.clear();
    }
}
