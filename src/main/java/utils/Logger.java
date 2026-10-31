package utils;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class Logger {
    private static final Gson GSON = new Gson();

    private Logger() {
    }

    public static void log(Path logFile, String status, String message) {
        try {
            logError(logFile, Map.of("status", status, "message", message));
        } catch (IOException loggingError) {
            System.err.println(message);
            System.err.println("could not write " + logFile + " > " + loggingError.getMessage());
        }
    }

    //@1ejun6 > append details as one json line > create log file if missing
    public static void logError(Path logFile, Map<String, ?> fields) throws IOException { //@1ejun6 > checked
        Path parent = logFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(logFile, GSON.toJson(fields) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        //@1ejun6 > converts map to json > adds a newline so each entry sits on its own line
        //@1ejun6 > creates file if it doesnt exist
    }
}
