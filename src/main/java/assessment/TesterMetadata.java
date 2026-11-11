package assessment;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;

/// Reads question:value metadata from the tester root.
public final class TesterMetadata {
    private static final Pattern passedStatement = Pattern.compile(
            "\\bSystem\\s*\\.\\s*out\\s*\\.\\s*println\\s*\\(\\s*\"Passed\"\\s*\\)\\s*;");

    private TesterMetadata() {}

    /** Loads existing metadata, or counts literal Passed statements and saves a missing entry. */
    public static synchronized int loadCaseCount(Path root, String questionId, Path source) throws IOException {
        Integer existing = readCaseCounts(root).get(questionId.toLowerCase(Locale.ROOT));
        if (existing != null) return existing;
        int count = (int) passedStatement.matcher(Files.readString(source)).results().count();
        return count > 0 ? recordCaseCount(root, questionId, count) : 0;
    }

    /** Adds a missing count; serializes read/modify/write across callers. */
    public static synchronized int recordCaseCount(Path root, String questionId, int count) throws IOException {
        if (count <= 0 || questionId == null || !questionId.matches("[A-Za-z0-9_]+")) {
            throw new IOException("invalid question ID or test-case count");
        }
        Path file = root.resolve("numTestCases.txt");
        String existing = Files.exists(file) ? Files.readString(file) : "";
        if (!existing.isBlank()) {
            Integer recorded = readCaseCounts(root).get(questionId.toLowerCase(Locale.ROOT));
            if (recorded != null) return recorded;
        }
        Files.createDirectories(root);
        String separator = existing.isEmpty() || existing.endsWith("\n") || existing.endsWith("\r") ? "" : "\n";
        Path temporary = Files.createTempFile(root, ".case-counts-", ".tmp");
        try {
            Files.writeString(temporary, existing + separator + questionId + ":" + count + "\n");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                JsonLineLogger.getInstance().logException(Status.WARNING,
                        "TesterMetadata", "atomic metadata replacement unavailable; using normal replacement", unsupported);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return count;
    }

    public static Map<String, Double> readMarks(Path root) throws IOException {
        var marks = read(root.resolve("marks.txt"));
        for (var entry : marks.entrySet()) {
            if ((!Double.isFinite(entry.getValue() * 2) || entry.getValue() <= 0 || entry.getValue() * 2 != Math.floor(entry.getValue() * 2)))
                throw new IOException("marks must be positive whole or half marks > " + entry.getKey());
        }
        return marks;
    }

    public static Map<String, Integer> readCaseCounts(Path root) throws IOException {
        Path file = root.resolve("numTestCases.txt");
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!Files.exists(file) || Files.readString(file).isBlank()) return counts;
        for (var entry : read(file).entrySet()) {
            double value = entry.getValue();
            if (value != Math.rint(value) || value > Integer.MAX_VALUE) {
                throw new IOException("invalid test-case count > " + entry.getKey());
            }
            counts.put(entry.getKey(), (int) value);
        }
        return counts;
    }

    private static Map<String, Double> read(Path file) throws IOException {
        Map<String, Double> values = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split(":", -1);
            try {
                if (fields.length != 2 || fields[0].isBlank()) throw new IllegalArgumentException();
                String id = fields[0].strip().toLowerCase(Locale.ROOT);
                double value = Double.parseDouble(fields[1].strip());
                if (!Double.isFinite(value) || value <= 0 || values.putIfAbsent(id, value) != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("invalid or duplicate metadata at " + file + ":" + lineNumber, error);
            }
        }
        if (values.isEmpty()) throw new IOException("no question metadata in " + file);
        return values;
    }
}
