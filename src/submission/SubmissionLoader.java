package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * find all student zip submissions &&& extracts each one into its own folder
 * inside workspace
 *
 * @param submissionFolder > containing student zip files
 * @param workspaceFolder > where submissions will be extracted
 * @return > list of extracted submission folder paths
 * @throws IOException > if files cannot be read ||| extracted
 */

public class SubmissionLoader {

    public static List<Path> loadSubmissions(Path submissionFolder, Path workspaceFolder) throws IOException {

        List<Path> studentSubmissionPaths = findZipFiles(submissionFolder);

        List<Path> extractedFolders = new ArrayList<>();

        //2. extract each zip
        for (Path studentSubmissionPath : studentSubmissionPaths) {
            String zipFileName = studentSubmissionPath.getFileName().toString(); //eg. alice.tan.2025.zip

            try {
                String folderName = zipFileName.substring(0, zipFileName.length() - 4); //eg. alice.tan.2025

                if (folderName.isEmpty() || folderName.equals(".") || folderName.equals("..")) {
                    throw new IOException("invalid submission filename > " + zipFileName);
                }

                //eg. workspace/alice.tan.2025
                Path destinationFolder = workspaceFolder.resolve(folderName.toLowerCase()); //where this zip should be extracted

                if (Files.exists(destinationFolder)) {
                    throw new IOException("submission folder already exists > " + destinationFolder);
                }

                List<Path> extractedFolder = ZipExtractor.ExtractZipFiles(studentSubmissionPath, destinationFolder);
                /// TODO: keep the questions folder into another class with the extract folder and student details
                extractedFolders.add(destinationFolder);

            } catch (IOException error) {
                System.err.println("could not process " + zipFileName + " > " + error.getMessage());
            }
        }

        return extractedFolders;
    }

    public static List<Path> findZipFiles(Path submissionFolder) throws IOException {
        //1. find all zip files
        try (Stream<Path> paths = Files.list(submissionFolder)) { //lists files directly inside
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) //case insenstive
                    .sorted().toList();
        }
    }

}
