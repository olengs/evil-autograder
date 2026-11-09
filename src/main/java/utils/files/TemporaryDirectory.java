package utils.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class TemporaryDirectory implements AutoCloseable { //owns a temporary directory & deletes it when closed
    private final Path path;
    private final Object fileKey;

    public TemporaryDirectory(String prefix) throws IOException {
        Path created = Files.createTempDirectory(prefix);
        path = created.getParent().toRealPath().resolve(created.getFileName());
        fileKey = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        if (fileKey == null) throw new IOException("Cannot identify temporary directory safely > " + path);
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        FileUtils.deleteDirectory(path, fileKey);
    }
}
