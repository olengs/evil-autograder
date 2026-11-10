package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.commons.io.file.PathUtils;

public final class FileUtils { //lists files, copies replacement files & deletes directories
    private FileUtils() {}

    //lists files & folders beneath directory > depth 1 lists direct children > caller must close stream
    public static Stream<Path> streamPaths(Path directory, int maxDepth) throws IOException {
        return maxDepth == 1 ? Files.list(directory) : Files.walk(directory, maxDepth).skip(1);
    }

    //lists direct regular files > caller must close returned stream
    public static Stream<Path> streamRegularFiles(Path directory) throws IOException {
        return streamPaths(directory, 1).filter(Files::isRegularFile);
    }

    // Commons IO validates file types, creates parents, and replaces the destination.
    public static void copyFileReplacing(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("copy source is not a regular file > " + source);
        // Keep the identity check: different filenames can refer to the same hard-linked file.
        if (Files.exists(destination) && Files.isSameFile(source, destination))
            throw new IOException("source & destination refer to same file");
        try {
            org.apache.commons.io.FileUtils.copyFile(source.toFile(), destination.toFile(), false);
        } catch (IllegalArgumentException error) {
            // Callers handle invalid submission paths as I/O failures, not application failures.
            throw new IOException(error.getMessage(), error);
        }
    }

    public static void deleteDirectory(Path folder) throws IOException {
        if (folder == null) {
            throw new IOException("Path is null");
        }
        
        Path path = folder.toAbsolutePath().normalize();
        
        if (path.getParent() == null) {
            throw new IOException("Cannot delete a filesystem root > " + path);
        }
        
        PathUtils.deleteDirectory(path);
    }
}
