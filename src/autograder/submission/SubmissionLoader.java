package autograder.submission;

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

    private ZipExtractor zipExtractor = new ZipExtractor();

    public List<Path> loadSubmissions(Path submissionFolder, Path workspaceFolder) throws IOException {

        List<Path> studentSubmissionPaths;

        //1. find all zip files
        try (Stream<Path> paths = Files.list(submissionFolder)) { //lists files directly inside

            studentSubmissionPaths = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")).toList(); //case insenstive
        }

        List<Path> extractedFolders = new ArrayList<>();

        //2. extract each zip
        for (Path studentSubmissionPath : studentSubmissionPaths) {
            System.out.println("processing > " + studentSubmissionPath);

            String zipFileName = studentSubmissionPath.getFileName().toString(); //eg. alice.tan.2025.zip

            try {
                String folderName = zipFileName.substring(0, zipFileName.length() - 4); //eg. alice.tan.2025

                if (folderName.isEmpty() || folderName.equals(".") || folderName.equals("..")) {
                    throw new IOException("invalid submission filename > " + zipFileName);
                }

                //eg. workspace/alice.tan.2025
                Path destinationFolder = workspaceFolder.resolve(folderName); //where this zip should be extracted

                if (Files.exists(destinationFolder)) {
                    throw new IOException("submission folder already exists > " + destinationFolder);
                }

                Path extractedFolder = zipExtractor.extract(studentSubmissionPath, destinationFolder); //extract zip
                extractedFolders.add(extractedFolder);
            } catch (IOException error) {
                System.err.println("could not process " + zipFileName + " > " + error.getMessage());
            }
        }

        return extractedFolders;
    }

}
