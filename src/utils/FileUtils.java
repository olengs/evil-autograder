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

    public enum CopyOptions {
        DEFAULT,
        REPLACE_EXISTING
    }

    public static synchronized void CopyDirectorySafe(Path from, Path to, CopyOptions option) throws IOException {
        if (option == null) {
            throw new IOException("Copy option is required");
        }
        Path source = ValidatePath(from);
        Path dest = ValidatePath(to);
        if (!isRecursive(from, to)) {
            throw new IOException("Destination cannot be inside the source directory: " + to);
        }
        if (!Files.isDirectory(source)) {
            throw new IOException("Copy source is not a directory: " + from);
        }
        if (!Files.isDirectory(dest)) {
            throw new IOException("Copy destination is not a directory: " + to);
        }

        try (FileChannel lockChannel = FileChannel.open(COPY_LOCK,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = lockChannel.lock()) {
            copyDirectory(source, dest, option);
        }
    }

    public static void CopyDirectory(Path from, Path to, CopyOptions option) throws IOException {
        if (option == null) {
            throw new IOException("Copy option is required");
        }
        Path source = ValidatePath(from);
        Path dest = ValidatePath(to);
        if (!isRecursive(from, to)) {
            throw new IOException("Destination cannot be inside the source directory: " + to);
        }
        if (!Files.isDirectory(source)) {
            throw new IOException("Copy source is not a directory: " + from);
        }
        if (!Files.isDirectory(dest)) {
            throw new IOException("Copy destination is not a directory: " + to);
        }
        copyDirectory(source, dest, option);
    }

    private static void copyDirectory(Path sourceRoot, Path destinationRoot, CopyOptions option) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Path target = destinationRoot.resolve(sourceRoot.relativize(directory));
                if (Files.isSymbolicLink(target)) {
                    throw new IOException("Destination contains a symbolic link: " + target);
                }
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
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
    
    public static void CopyFileSafe(Path source, Path destination, CopyOptions option) throws IOException {
        
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

    public static boolean isRecursive(Path p1, Path p2) {
        return p1.startsWith(p2) || p2.startsWith(p1);
    }
}
