//lists submission archives using shared filename convention
//keeps direct & sorted zip discovery available without depending on submission preparation

package submission;

import utils.files.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class SubmissionArchives {
    private SubmissionArchives() {}

    public static List<Path> listZipFiles(Path submissionFolder) throws IOException {
        try (Stream<Path> paths = FileUtils.streamRegularFiles(submissionFolder)) {
            return paths.filter(SubmissionFileNames::hasZipExtension).sorted().toList();
        }
    }
}
