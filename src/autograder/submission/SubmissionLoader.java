// Finds student ZIPs and extracts separate working copies.
// Returns original-archive and extraction-root associations.

package autograder.submission;

import autograder.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SubmissionLoader {

    /** Compatibility entry point for callers that only need extracted directory paths. */
    public static List<Path> loadSubmissions(Path submissionFolder, Path workspaceFolder) throws IOException {
        List<Path> folders = new ArrayList<>();
        for (ExtractedSubmission extracted : loadExtractedSubmissions(submissionFolder, workspaceFolder)) {
            folders.add(extracted.rootDirectory());
        }
        return folders;
    }

    public static List<ExtractedSubmission> loadExtractedSubmissions(
            Path submissionFolder, Path workspaceFolder) throws IOException {
        return loadExtractedSubmissions(findZipFiles(submissionFolder), workspaceFolder);
    }

    /** Reuses an already discovered ZIP list; one failed archive does not stop later archives. */
    public static List<ExtractedSubmission> loadExtractedSubmissions(
            List<Path> zipFiles, Path workspaceFolder) {
        List<ExtractedSubmission> extracted = new ArrayList<>();
        for (Path zipFile : zipFiles) {
            try {
                Path destination = workspaceFolder.resolve(SubmissionFileNames.extractionFolderName(zipFile));
                if (Files.exists(destination)) {
                    throw new IOException("submission folder already exists > " + destination);
                }
                ZipExtractor.extractZip(zipFile, destination);
                extracted.add(new ExtractedSubmission(zipFile, destination));
            } catch (IOException error) {
                System.err.println("could not process " + zipFile.getFileName() + " > " + error.getMessage());
            }
        }
        return extracted;
    }

    public static List<Path> findZipFiles(Path submissionFolder) throws IOException {
        try (Stream<Path> files = FileUtils.streamRegularFiles(submissionFolder)) {
            return files.filter(SubmissionFileNames::hasZipExtension).sorted().toList();
        }
    }
}
