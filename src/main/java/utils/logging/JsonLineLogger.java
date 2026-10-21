package utils.logging;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class JsonLineLogger { //appends structured log entries as JSON lines
    private static final Gson gson = new Gson();

    private JsonLineLogger() {
    }

    public static void append(Path logFile, Map<String, ?> fields) throws IOException {
        Path parent = logFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(logFile, gson.toJson(fields) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
