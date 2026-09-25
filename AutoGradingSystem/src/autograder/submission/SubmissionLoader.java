package autograder.submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/*
Creator: XL
*/

public class SubmissionLoader {

    private ZipExtractor zipExtractor = new ZipExtractor();

    /**
     * Finds all student ZIP submissions and extracts each one
     * into its own folder inside the workspace.
     *
     * @param submissionFolder folder containing student ZIP files
     * @param workspaceFolder  folder where submissions will be extracted
     * @return list of extracted submission folder paths
     * @throws IOException if files cannot be read or extracted
     */
    public List<Path> loadSubmissions(Path submissionFolder,Path workspaceFolder) 
        throws IOException {

        List<Path> studentSubmissionPaths;

        // 1. Find all ZIP files
        try (Stream<Path> paths = Files.list(submissionFolder)) {

            studentSubmissionPaths = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString()
                    .toLowerCase()
                    .endsWith(".zip"))
                    .toList();
        }

        List<Path> extractedFolders = new ArrayList<>();

        // 2. Extract each ZIP
        for (Path studentSubmissionPath : studentSubmissionPaths) {

            System.out.println(
                    "Processing: " + studentSubmissionPath);

            // alice.tan.2025.zip
            String zipFileName = studentSubmissionPath.getFileName().toString();

            // alice.tan.2025
            String folderName = zipFileName.substring(0,zipFileName.length() - 4);

            // workspace/alice.tan.2025
            Path destinationFolder = workspaceFolder.resolve(folderName);

            // Extract ZIP
            Path extractedFolder = zipExtractor.extract(studentSubmissionPath,destinationFolder);

            extractedFolders.add(extractedFolder);
        }

        return extractedFolders;
    }



}