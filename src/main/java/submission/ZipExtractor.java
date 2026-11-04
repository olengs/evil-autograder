// Extracts one ZIP archive into the workspace supplied by its caller.
// Rejects entries that would escape that directory and reports extraction failures.

package submission;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

public class ZipExtractor {
    /** @1ejun6 > extracts one zip file into given destination folder
     *
     * @param zipFile > zip file to extract
     * @param destinationFolder > where zip will be extracted
     * @return > extracted destination folder
     * @throws IOException > if zip cannot be read \ extracted
     */
    public static Path extract(Path zipFile, Path destinationFolder) throws IOException { //@1ejun6 > returns one extraction root instead of a folder list
        Path extractionDirectory = destinationFolder.toAbsolutePath().normalize(); //@1ejun6 > make destination path absolute & normalise
        Files.createDirectories(extractionDirectory); //@1ejun6 > create destination folder if it does not exist

        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            if (archive.size() == 0) {
                return extractionDirectory;
            }
        }

        try (InputStream inputStream = Files.newInputStream(zipFile); //@1ejun6 > reads raw bytes from file
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) { //@1ejun6 > read files & folders inside it

            ZipEntry entry; //@1ejun6 > file \ folder
            while ((entry = zipInputStream.getNextEntry()) != null) { //@1ejun6 > read each file \ directory inside zip
                Path outputPath = extractionDirectory.resolve(entry.getName()).normalize(); //@1ejun6 > builds location where current zip item should go

                if (!outputPath.startsWith(extractionDirectory)) { //@1ejun6 > dont allow zip contents to escape destination folder
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) { //@1ejun6 > eg. q1
                    Files.createDirectories(outputPath);
                } else { //@1ejun6 > eg. q1/q1a.java
                    Path parentFolder = outputPath.getParent();

                    if (parentFolder != null) {
                        Files.createDirectories(parentFolder);
                    }

                    Files.copy(zipInputStream, outputPath); //@1ejun6 > write entrys bytes to its destination
                    //@1ejun6 > copies contents of current zip file entry into destination path
                }

                zipInputStream.closeEntry();
            }
        }

        return extractionDirectory; //@1ejun6 > caller owns this folder & cleans it up
    }
}
