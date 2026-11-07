//extracts one zip archive into destination directory supplied by its caller
//rejects entries that escape that directory & leaves workspace ownership & cleanup to caller

package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipExtractor {
    private ZipExtractor() {
    }

    //caller owns returned extraction directory & must clean it up
    public static Path extract(Path zipFile, Path destinationFolder) throws IOException {
        Path extractionDirectory = destinationFolder.toAbsolutePath().normalize();
        Files.createDirectories(extractionDirectory);

        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path outputPath = extractionDirectory.resolve(entry.getName()).normalize();

                if (!outputPath.startsWith(extractionDirectory)) throw new IOException("Invalid zip entry: " + entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Path parentFolder = outputPath.getParent();

                    if (parentFolder != null) Files.createDirectories(parentFolder);

                    try (var contents = archive.getInputStream(entry)) {
                        Files.copy(contents, outputPath);
                    }
                }
            }
        }
        return extractionDirectory;
    }
}
