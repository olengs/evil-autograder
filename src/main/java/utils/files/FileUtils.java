package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

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

    // Use directory-relative operations that never follow links, including during concurrent swaps.
    public static void deleteDirectory(Path folder) throws IOException {
        deleteDirectory(folder, null);
    }

    static void deleteDirectory(Path folder, Object expectedKey) throws IOException {
        if (folder == null) throw new IOException("Path is null");
        Path path = folder.toAbsolutePath().normalize();
        if (path.getParent() == null) throw new IOException("Cannot delete a filesystem root > " + path);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path.getParent())) {
            if (!(stream instanceof SecureDirectoryStream<Path> parent)) {
                throw new IOException("Secure directory operations unavailable; refusing cleanup > " + path);
            }
            deleteEntry(parent, path.getFileName(), expectedKey);
        }
    }

    private static void deleteEntry(SecureDirectoryStream<Path> parent, Path name, Object expectedKey) throws IOException {
        BasicFileAttributes before = parent.getFileAttributeView(name, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS).readAttributes();
        if (expectedKey != null && !Objects.equals(expectedKey, before.fileKey())) {
            throw new IOException("Temporary directory was replaced; refusing cleanup > " + name);
        }
        if (!before.isDirectory()) {
            if (expectedKey != null) throw new IOException("Temporary directory is no longer a directory > " + name);
            parent.deleteFile(name); // deletes a symlink itself, never its target
            return;
        }
        try (SecureDirectoryStream<Path> child = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
            BasicFileAttributes opened = child.getFileAttributeView(BasicFileAttributeView.class).readAttributes();
            if (before.fileKey() == null || !Objects.equals(before.fileKey(), opened.fileKey())) {
                throw new IOException("Directory changed during cleanup > " + name);
            }
            for (Path entry : child) deleteEntry(child, entry.getFileName(), null);
        }
        // A concurrent swap cannot make deleteDirectory follow a symlink.
        parent.deleteDirectory(name);
    }
}
