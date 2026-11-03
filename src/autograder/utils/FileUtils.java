// Provides shared filesystem operations without validation policy.
// Streams direct regular files with caller-owned resource cleanup.

package autograder.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class FileUtils {
    private FileUtils() {}

    /**
     * Returns a lazy, non-recursive stream of regular files, following symbolic links
     * as Files.isRegularFile normally does. The caller must close the returned stream.
     * Missing-directory, ordering, and iteration errors retain Files.list behavior.
     */
    public static Stream<Path> streamRegularFiles(Path directory) throws IOException {
        return Files.list(directory).filter(Files::isRegularFile);
    }
}
