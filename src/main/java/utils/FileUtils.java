// Provides shared file listing, copy, deletion, and path checks for the pipeline.
// Keeps filesystem operations and their existing error handling in one place.

package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;

public class FileUtils {
    /** Lists direct regular files; the caller must close the returned stream. */
    public static Stream<Path> streamRegularFiles(Path directory) throws IOException {
        return Files.list(directory).filter(Files::isRegularFile);
    }

    /** Replaces an existing file after validation; callers create parent directories. */
    public static void copyFileReplacing(Path source, Path destination) throws IOException {
        checkFileCopy(source, destination);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    // Reject non-file paths and copying a file onto itself before replacement.
    private static void checkFileCopy(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("copy source is not a regular file > " + source);
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) throw new IOException("destination is not a regular file > " + destination);
            if (Files.isSameFile(source, destination)) throw new IOException("source & destination refer to same file");
        }
    }

    public static void deleteDirectory(Path folder) throws IOException {
        //@1ejun6 > delete files first > then their empty folders > then root folder
        Path root = resolveExistingPath(folder);
        
        if (root.getParent() == null) {
            throw new IOException("Cannot delete a filesystem root > " + root);
        }

        PathUtils.deleteDirectory(root);
    }

    public static Path resolveExistingPath(Path value) throws IOException {
        if (value == null) {
            throw new IOException("Path is null");
        }

        Path path;
        try {
            path = value.toAbsolutePath().normalize();
        } catch (InvalidPathException error) {
            throw new IOException("Invalid path >" + value.toString(), error);
        }
        return path.toRealPath(); //@1ejun6 > require an existing path & resolve symbolic links > can throw IOException
    }
}
