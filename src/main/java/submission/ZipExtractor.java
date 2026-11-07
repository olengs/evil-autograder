package submission;

import utils.process.Interruptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipExtractor { //extracts ZIP entries while rejecting paths outside destination
    private ZipExtractor() {
    }

    //caller owns extraction directory & must clean it up
    public static void extract(Path zipFile, Path destinationFolder) throws IOException {
        Interruptions.check("submission extraction interrupted");
        Path extractionDirectory = destinationFolder.toAbsolutePath().normalize();
        Files.createDirectories(extractionDirectory);

        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            byte[] buffer = new byte[8192];
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                Interruptions.check("submission extraction interrupted");
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
                            Interruptions.check("submission extraction interrupted");
                            int count = contents.read(buffer);
                            if (count == -1) break;
                            Interruptions.check("submission extraction interrupted");
                            output.write(buffer, 0, count);
                        }
                    }
                }
            }
        }
        Interruptions.check("submission extraction interrupted");
    }
}
