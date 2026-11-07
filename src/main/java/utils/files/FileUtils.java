package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;

public final class FileUtils { //lists files, copies replacement files & deletes directories
    private FileUtils() {}

    //lists direct regular files > caller must close returned stream
    public static Stream<Path> streamRegularFiles(Path directory) throws IOException {
        return Files.list(directory).filter(Files::isRegularFile);
    }

    //replaces an existing file after validation > callers create parent directories
    public static void copyFileReplacing(Path source, Path destination) throws IOException {
        checkFileCopy(source, destination);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    //reject non-file paths & copying a file onto itself before replacement

    private static void checkFileCopy(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("copy source is not a regular file > " + source);
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) throw new IOException("destination is not a regular file > " + destination);
            if (Files.isSameFile(source, destination)) throw new IOException("source & destination refer to same file");
        }
    }

    public static void deleteDirectory(Path folder) throws IOException {
        Path root = resolveExistingPath(folder);
        if (root.getParent() == null) throw new IOException("Cannot delete a filesystem root > " + root);
        PathUtils.deleteDirectory(root);
    }

    private static Path resolveExistingPath(Path value) throws IOException {
        if (value == null) throw new IOException("Path is null");

        Path path;
        try {
            path = value.toAbsolutePath().normalize();
        } catch (InvalidPathException error) {
            throw new IOException("Invalid path >" + value.toString(), error);
        }
        return path.toRealPath();
    }
}
