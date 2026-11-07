//writes csv rows with opencsv escaping & reports write failures
//creates parent directories for creating a student list when none is provided & completed scoresheet output

package report;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvWriter {
    private CsvWriter() {}

    public static void write(Path file, List<String[]> rows) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        
        try (var writer = new CSVWriter(Files.newBufferedWriter(file))) {
            writer.writeAll(rows, false);
            if (writer.checkError()) throw new IOException("cannot write csv > " + file, writer.getException());
        }
    }
}
