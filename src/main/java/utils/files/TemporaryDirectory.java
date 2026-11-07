//owns a temporary directory & deletes its contents when closed
//lets extraction & grading use try-with-resources to clean up working files even when a step fails

package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
