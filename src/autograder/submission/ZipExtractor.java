package autograder.submission;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipExtractor {
    /**
     * extracts one zip file into given destination folder
     *
     * @param zipFile > zip file to extract
     * @param destinationFolder > where zip will be extracted
     * @return > extracted destination folder
     * @throws IOException > if zip cannot be read ||| extracted
     */
    public static ArrayList<Path> ExtractZipFiles(Path zipFile, Path destinationFolder) throws IOException {
        ArrayList<Path> extractedTopLevelFolders = new ArrayList<>();
        Path normalizedDestinationPath = destinationFolder.toAbsolutePath().normalize(); //make destination path absolute &&& normalise
        Files.createDirectories(normalizedDestinationPath); //create destination folder if it does not exist

        try ( InputStream inputStream = Files.newInputStream(zipFile); //reads raw bytes from file
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) { //read files &&& folders inside it
            
            ZipEntry entry; //file ||| folder
            while ((entry = zipInputStream.getNextEntry()) != null) { //read every item inside zip
                Path outputPath = normalizedDestinationPath.resolve(entry.getName()).normalize(); //builds location where current zip item should go

                if (!outputPath.startsWith(normalizedDestinationPath)) { //dont allow zip contents to escape destination folder
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) { //eg. q1
                    Files.createDirectories(outputPath);
                    extractedTopLevelFolders.add(outputPath);
                } 
                else { //eg. q1/q1a.java
                    Path parentFolder = outputPath.getParent();

                    if (parentFolder != null) {
                        Files.createDirectories(parentFolder);
                    }

                    Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING); //copies contents of current zip file entry into destination path
                }

                zipInputStream.closeEntry();
            }
        }

        return extractedTopLevelFolders;
    }
}
