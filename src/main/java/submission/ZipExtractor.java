package submission;

import java.io.IOException;
import java.nio.file.Path;
import utils.FileUtils;

/** Compatibility entry point; file operations belong to FileUtils. */
public class ZipExtractor {
    public static Path extract(Path zipFile, Path destinationFolder) throws IOException {
        return FileUtils.extractZip(zipFile, destinationFolder);
    }
}
