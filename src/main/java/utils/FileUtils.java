package utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FileUtils { //copies files & folders & deletes temporary folders
    public enum CopyOptions {
        DEFAULT,
        REPLACE_EXISTING
    }

    //#region file copy
    private static final Path COPY_LOCK = Path.of(System.getProperty("java.io.tmpdir"), "evil-autograder-directory-copy.lock");

    public record CopyPaths(Path source, Path destination) {}

    public static synchronized void CopyDirectorySafe(Path from, Path to, CopyOptions option) throws IOException {
        CopyPaths paths = checkCopyPaths(from, to, option);

        try (FileChannel lockChannel = FileChannel.open(COPY_LOCK,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = lockChannel.lock()) {

            copyDirectory(paths.source, paths.destination, option);
        }
    }

    public static void copyDirectory(Path from, Path to, CopyOptions option) throws IOException {
        CopyPaths paths = checkCopyPaths(from, to, option);
        copyDirectoryContents(paths.source, paths.destination,  option);
    }

    private static CopyPaths checkCopyPaths(Path from, Path to, CopyOptions option) throws IOException { //@1ejun6 > checks that both folders are valid before copying
        if (option == null) {
            throw new IOException("copy option is required");
        }
        Path source = resolveExistingPath(from);
        Path destination = resolveExistingPath(to); //@1ejun6 > destination folder must already exist too
        if (pathsOverlap(source, destination)) {
            throw new IOException("source & destination overlap > " + to);
        }
        if (!Files.isDirectory(source)) {
            throw new IOException("copy source is not a directory > " + from);
        }
        if (!Files.isDirectory(destination)) {
            throw new IOException("copy destination is not a directory > " + to);
        }
        return new CopyPaths(source, destination);
    }

    private static void copyDirectoryContents(Path sourceRoot, Path destinationRoot, CopyOptions option) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Path target = destinationRoot.resolve(sourceRoot.relativize(directory));
                if (Files.isSymbolicLink(target)) {
                    throw new IOException("destination contains a symbolic link > " + target);
                }
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException("source contains a non-regular file > " + file);
                }
                copyFile(file, destinationRoot.resolve(sourceRoot.relativize(file)), option);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyFile(Path source, Path destination, CopyOptions option) throws IOException {
        if (option == null) {
            throw new IOException("copy option is required");
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("copy source is not a regular file > " + source);
        }
        //@1ejun6 > destination parent folder must already exist
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) {
                throw new IOException("destination is not a regular file > " + destination);
            }
            if (Files.isSameFile(source, destination)) {
                throw new IOException("source & destination refer to same file");
            }
        }

        if (option == CopyOptions.REPLACE_EXISTING) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(source, destination);
        }
    }

    public static void CopyFileSafe(Path source, Path destinationFolder, CopyOptions option) throws IOException {
        Path sourceKey = ValidatePath(source);
        if (!Files.isRegularFile(sourceKey)) {
            throw new IOException("Copy source is not a regular file: " + source);
        }
 
        if (destinationFolder == null) {
            throw new IOException("Copy destination folder path is required");
        }
 
        Path dest = ValidatePath(destinationFolder);
        if (dest == null || !Files.isDirectory(dest)) {
            throw new IOException("Copy destination parent is not a directory: " + dest);
        }

        Path destinationKey = dest.resolve(sourceKey.getFileName()); 
        Path first = sourceKey.compareTo(destinationKey) < 0 ? sourceKey : destinationKey;
        Path second = first.equals(sourceKey) ? destinationKey : sourceKey;
 
        // create locks for both channel
        try (FileChannel firstChannel = FileChannel.open(fileLockPath(first),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileChannel secondChannel = FileChannel.open(fileLockPath(second),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
 
            while (true) {
                //recursively try to lock, if fail or waiting, exit and wait to prevent deadlock
                try (FileLock firstLock = firstChannel.tryLock()) {
                    if (firstLock != null) {
                        try (FileLock secondLock = secondChannel.tryLock()) {
                            if (secondLock != null) {
                                copyFile(sourceKey, destinationKey, option);
                                return;
                            }
                        }
                    }
                } catch (OverlappingFileLockException ignored) {
                    // A thread in this JVM already holds one of the file locks.
                }
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(1, 11));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while waiting for file locks");
                }
            } 
        }
    }
 
    private static Path fileLockPath(Path file) {
        UUID id = UUID.nameUUIDFromBytes(file.toString().getBytes(StandardCharsets.UTF_8));
        return Path.of(System.getProperty("java.io.tmpdir"), "evil-autograder-file-" + id + ".lock");
    }

    //#endregion file copy
    
    public static void deleteDirectory(Path folder) throws IOException {
        //@1ejun6 > delete files first > then their empty folders > then root folder
        Path root = resolveExistingPath(folder);
        
        if (root.getParent() == null) {
            throw new IOException("Cannot delete a filesystem root > " + root);
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
