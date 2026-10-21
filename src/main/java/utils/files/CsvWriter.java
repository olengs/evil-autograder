package utils.files;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import utils.process.Interruptions;

public final class CsvWriter { //writes escaped CSV rows before replacing destination file
    private CsvWriter() {}

    public static void write(Path file, List<String[]> rows) throws IOException {
        Interruptions.check("csv writing interrupted");
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".autograder-csv-", ".tmp");
        try {
            try (var writer = new CSVWriter(Files.newBufferedWriter(temporary))) {
                for (String[] row : rows) {
                    Interruptions.check("csv writing interrupted");
                    writer.writeNext(row, false);
                }
                if (writer.checkError()) throw new IOException("cannot write csv > " + file, writer.getException());
            }
            //replace only after writing completes > cancellation keeps previous output intact
            Interruptions.check("csv writing interrupted");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Interruptions.check("csv writing interrupted");
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
