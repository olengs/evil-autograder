package autograder.submission;

import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipExtractor {

    /**
     * Extracts one ZIP file into the given destination folder.
     *
     * @param zipFile the ZIP file to extract
     * @param destinationFolder folder where the ZIP will be extracted
     * @return the extracted destination folder
     * @throws IOException if the ZIP cannot be read or extracted
     */
    public Path extract(Path zipFile,Path destinationFolder) 
        throws IOException 
        {

        // Make the destination path absolute and clean
        Path normalizedDestination = destinationFolder.toAbsolutePath().normalize();

        // Create destination folder if it does not exist
        Files.createDirectories(normalizedDestination);

        // Open ZIP file
        try (
            InputStream inputStream = Files.newInputStream(zipFile);

            ZipInputStream zipInputStream = new ZipInputStream(inputStream)
        ) {

            ZipEntry entry;

            // Read every item inside the ZIP
            while ((entry = zipInputStream.getNextEntry()) != null) {

                Path outputPath =normalizedDestination
                                .resolve(entry.getName())
                                .normalize();

                // Security check:
                // Don't allow ZIP contents to escape the destination folder
                if (!outputPath.startsWith(normalizedDestination)) {
                    throw new IOException("Invalid ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    // Example: Q1/
                    Files.createDirectories(outputPath);

                } else {
                    // Example: Q1/Q1a.java
                    Path parentFolder = outputPath.getParent();
                    
                    if (parentFolder != null) {
                        Files.createDirectories(parentFolder);
                    }

                    Files.copy(
                            zipInputStream,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                zipInputStream.closeEntry();
            }
        }

        return normalizedDestination;
    }
}