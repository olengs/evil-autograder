package submission;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import utils.files.FileUtils;

public final class SubmissionArchives { //lists submission ZIP files in sorted order
    private SubmissionArchives() {}

    public static List<Path> listZipFiles(Path submissionFolder) throws IOException {
        try (Stream<Path> paths = FileUtils.streamRegularFiles(submissionFolder)) {
            return paths.filter(SubmissionFileNames::hasZipExtension).sorted().toList();
        }
    }
}
