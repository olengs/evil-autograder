package utils;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvWriter { //OpenCSV handles separators, quotes & line breaks
    private CsvWriter() {}

    //@1ejun6 > create parent folders > write csv rows > report write errors
    public static void write(Path file, List<String[]> rows) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (var writer = new CSVWriter(Files.newBufferedWriter(file))) {
            writer.writeAll(rows, false);
            if (writer.checkError()) throw new IOException("cannot write csv > " + file, writer.getException());
        }
    }
}
