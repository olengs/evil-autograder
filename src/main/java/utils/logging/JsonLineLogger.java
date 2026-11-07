//appends structured log entries as individual json lines
//records corrected & skipped student list rows while student list reader continues reading scoresheet

package utils.logging;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class JsonLineLogger {
    private static final Gson gson = new Gson();

    private JsonLineLogger() {
    }

    public static void append(Path logFile, Map<String, ?> fields) throws IOException {
        Files.writeString(logFile, gson.toJson(fields) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
