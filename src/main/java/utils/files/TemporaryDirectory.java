package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class TemporaryDirectory implements AutoCloseable { //owns a temporary directory & deletes it when closed
    private final Path path;

    public TemporaryDirectory(String prefix) throws IOException {
        Path created = Files.createTempDirectory(prefix);
        path = created.getParent().toRealPath().resolve(created.getFileName());
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        // Reject a replaced directory before handing it to the shared deletion utility.
        BasicFileAttributes current = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!current.isDirectory()) {
            throw new IOException("Temporary directory identity changed > " + path);
        }
        FileUtils.deleteDirectory(path);
    }
}
