package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns a temporary directory and deletes its contents when closed. */
public final class TemporaryDirectory implements AutoCloseable {
    private final Path path;

    public TemporaryDirectory(String prefix) throws IOException {
        path = Files.createTempDirectory(prefix);
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        FileUtils.deleteDirectory(path);
    }
}
