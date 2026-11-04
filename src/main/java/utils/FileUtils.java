// Provides shared file listing, copy, deletion, and path checks for the pipeline.
// Keeps filesystem operations and their existing error handling in one place.

package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;

public class FileUtils {
    /** Lists direct regular files; the caller must close the returned stream. */
    public static Stream<Path> streamRegularFiles(Path directory) throws IOException {
        return Files.list(directory).filter(Files::isRegularFile);
    }

    public enum CopyOptions {
        DEFAULT,
        REPLACE_EXISTING
    }

    //#region file copy
    public static void copyDirectory(Path from, Path to, CopyOptions option) throws IOException {
        if (option == null) throw new IOException("copy option is required");
        Path source = resolveExistingPath(from);
        Path destination = resolveExistingPath(to);
        if (pathsOverlap(source, destination)) throw new IOException("source & destination overlap > " + to);
        if (!Files.isDirectory(source)) throw new IOException("copy source is not a directory > " + from);
        if (!Files.isDirectory(destination)) throw new IOException("copy destination is not a directory > " + to);

        // Preserve the existing file-type checks before letting Commons IO copy the tree.
        try (var entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                Path target = destination.resolve(source.relativize(entry));
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.exists(target) && !Files.isDirectory(target)) {
                        throw new IOException("destination is not a directory > " + target);
                    }
                } else {
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("source contains a non-regular file > " + entry);
                    }
                    checkFileCopy(entry, target);
                }
            }
        }
        if (option == CopyOptions.REPLACE_EXISTING) {
            PathUtils.copyDirectory(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            PathUtils.copyDirectory(source, destination);
        }
    }

    public static void copyFile(Path source, Path destination, CopyOptions option) throws IOException {
        if (option == null) throw new IOException("copy option is required");
        checkFileCopy(source, destination);
        if (option == CopyOptions.REPLACE_EXISTING) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(source, destination);
        }
    }

    private static void checkFileCopy(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("copy source is not a regular file > " + source);
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) throw new IOException("destination is not a regular file > " + destination);
            if (Files.isSameFile(source, destination)) throw new IOException("source & destination refer to same file");
        }
    }

    //#endregion file copy
    
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

    public static boolean pathsOverlap(Path firstPath, Path secondPath) {
        //@1ejun6 > startswith checks path components > /input & /input2 do not overlap
        return firstPath.startsWith(secondPath) || secondPath.startsWith(firstPath);
    }
}
