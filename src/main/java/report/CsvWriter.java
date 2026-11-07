//writes csv rows with opencsv escaping & reports write failures
//creates parent directories for creating a student list when none is provided & completed scoresheet output

package report;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class CsvWriter {
    private CsvWriter() {}

    public static void write(Path file, List<String[]> rows) throws IOException {
        checkInterrupted();
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".autograder-csv-", ".tmp");
        try {
            try (var writer = new CSVWriter(Files.newBufferedWriter(temporary))) {
                for (String[] row : rows) {
                    checkInterrupted();
                    writer.writeNext(row, false);
                }
                if (writer.checkError()) throw new IOException("cannot write csv > " + file, writer.getException());
            }
            //replace only after writing completes > cancellation keeps previous output intact
            checkInterrupted();
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                checkInterrupted();
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("csv writing interrupted");
    }
}
