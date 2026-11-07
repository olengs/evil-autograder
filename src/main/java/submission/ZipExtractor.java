//extracts one zip archive into destination directory supplied by its caller
//rejects entries that escape that directory & leaves workspace ownership & cleanup to caller

package submission;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipExtractor {
    private ZipExtractor() {
    }

    //caller owns returned extraction directory & must clean it up
    public static Path extract(Path zipFile, Path destinationFolder) throws IOException {
        checkInterrupted();
        Path extractionDirectory = destinationFolder.toAbsolutePath().normalize();
        Files.createDirectories(extractionDirectory);

        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            byte[] buffer = new byte[8192];
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                checkInterrupted();
                ZipEntry entry = entries.nextElement();
                Path outputPath = extractionDirectory.resolve(entry.getName()).normalize();

                if (!outputPath.startsWith(extractionDirectory)) throw new IOException("Invalid zip entry: " + entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Path parentFolder = outputPath.getParent();

                    if (parentFolder != null) Files.createDirectories(parentFolder);

                    try (var contents = archive.getInputStream(entry);
                            var output = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        while (true) {
                            checkInterrupted();
                            int count = contents.read(buffer);
                            if (count == -1) break;
                            checkInterrupted();
                            output.write(buffer, 0, count);
                        }
                    }
                }
            }
        }
        checkInterrupted();
        return extractionDirectory;
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("submission extraction interrupted");
    }
}
