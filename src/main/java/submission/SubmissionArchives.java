package submission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import utils.files.FileUtils;

public final class SubmissionArchives { //lists submission ZIP files recursively in sorted order
    private SubmissionArchives() {}

    public static List<Path> listZipFiles(Path submissionFolder) throws IOException {
        try (Stream<Path> paths = FileUtils.streamPaths(submissionFolder, Integer.MAX_VALUE)) {
            return paths.filter(Files::isRegularFile).filter(SubmissionFileNames::hasZipExtension).sorted().toList();
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }
}
