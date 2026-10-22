package utils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {
    //#region file copy
    private static final Path COPY_LOCK = Path.of(System.getProperty("java.io.tmpdir"), 
                                        "evil-autograder-directory-copy.lock");

    private record CopyPaths(Path source, Path destination) {}

    public enum CopyOptions {
        DEFAULT,
        REPLACE_EXISTING
    }

    public static synchronized void CopyFilesSafe(Path from, Path to, CopyOptions option) throws IOException {
        try (FileChannel lockChannel = FileChannel.open(COPY_LOCK,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = lockChannel.lock()) {
            CopyPaths paths = validateCopyPaths(from, to, option);
            copyDirectory(paths.source(), paths.destination(), option);
        }
    }

    public static void CopyFiles(Path from, Path to, CopyOptions option) throws IOException {
        CopyPaths paths = validateCopyPaths(from, to, option);
        copyDirectory(paths.source(), paths.destination(), option);
    }

    private static CopyPaths validateCopyPaths(Path from, Path to, CopyOptions option) throws IOException {
        if (option == null) {
            throw new IOException("Copy option is required");
        }

        Path source = ValidatePath(from);
        Path destination = ValidatePath(to);
        if (destination.startsWith(source)) {
            throw new IOException("Destination cannot be inside the source directory: " + destination);
        }
        if (!Files.isDirectory(from)) {
            throw new IOException("Copy source is not a directory: " + from);
        }
        if (!Files.isDirectory(to)) {
            throw new IOException("Copy destination is not a directory: " + to);
        }
        return new CopyPaths(source, destination);
    }

    private static void copyDirectory(Path sourceRoot, Path destinationRoot, CopyOptions option) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Path target = destinationRoot.resolve(sourceRoot.relativize(directory));
                if (Files.isSymbolicLink(target)) {
                    throw new IOException("Destination contains a symbolic link: " + target);
                }
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException("Source contains a non-regular file: " + file);
                }
                CopyFile(file, destinationRoot.resolve(sourceRoot.relativize(file)), option);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void CopyFile(Path source, Path destination, CopyOptions option) throws IOException {
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) {
                throw new IOException("Destination is not a regular file: " + destination);
            }
            if (Files.isSameFile(source, destination)) {
                throw new IOException("Source and destination refer to the same file");
            }
        }

        if (option == CopyOptions.REPLACE_EXISTING) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(source, destination);
        }
    }
    //#endregion filecopy

    public static void DeleteFolder(Path folder) throws IOException {
        Path root = ValidatePath(folder);
        
        if (root.getParent() == null) {
            throw new IOException("Cannot delete a filesystem root: " + root);
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Path ValidatePath(Path value) throws IOException {
        if (value == null) {
            throw new IOException("Failed to validate null path");
        }

        Path path;
        try {
            path = value.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IOException("Invalid path:" + value.toString(), e);
        }
        return path.toRealPath();
    }
}
