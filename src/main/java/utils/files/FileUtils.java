package utils.files;

import java.io.IOException;
import java.nio.file.Files;
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
        if (!Files.isRegularFile(source)) throw new IOException("copy source is not a regular file > " + source);
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) throw new IOException("destination is not a regular file > " + destination);
            if (Files.isSameFile(source, destination)) throw new IOException("source & destination refer to same file");
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
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
