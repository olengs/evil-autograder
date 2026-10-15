package autograder.submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts one ZIP; naming corrections belong to SubmissionNormalizer. */
public class ZipExtractor {
    /** AI-assisted with Codex: rejects invalid archives and colliding paths before extraction. */
    public static ArrayList<Path> ExtractZipFiles(Path zipFile, Path destinationFolder) throws IOException {
        Path destination = destinationFolder.toAbsolutePath().normalize();
        try (ZipFile archive = new ZipFile(zipFile.toFile())) {
            List<? extends ZipEntry> entries = archive.stream().toList();
            validateEntries(entries, destination);
            Files.createDirectories(destination.getParent());
            Files.createDirectory(destination); //never merge an archive with an existing submission
            for (ZipEntry entry : entries) {
                Path output = entryPath(destination, entry);
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    CRC32 checksum = new CRC32();
                    try (CheckedInputStream input = new CheckedInputStream(archive.getInputStream(entry), checksum)) {
                        long size = Files.copy(input, output);
                        if (size != entry.getSize() || checksum.getValue() != entry.getCrc()) {
                            throw new IOException("corrupt ZIP entry > " + entry.getName());
                        }
                    }
                }
            }
        } catch (InvalidPathException error) {
            throw new IOException("invalid ZIP entry path > " + error.getInput(), error);
        }
        //Directory entries may be absent in a ZIP, so inspect the actual extracted tree.
        try (var children = Files.list(destination)) {
            return new ArrayList<>(children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted().toList());
        }
    }

    private static void validateEntries(List<? extends ZipEntry> entries, Path root) throws IOException {
        Map<String, String> spellings = new HashMap<>();
        Set<Path> files = new HashSet<>();
        Set<Path> directories = new HashSet<>();
        for (ZipEntry entry : entries) {
            Path output = entryPath(root, entry);
            for (Path path = output; !path.equals(root); path = path.getParent()) {
                String name = path.toString();
                String previous = spellings.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                if (previous != null && !previous.equals(name)) {
                    throw new IOException("ZIP path collision > " + previous + " and " + name);
                }
            }
            if (entry.isDirectory()) {
                if (files.contains(output)) { throw new IOException("ZIP file/directory collision > " + entry.getName()); }
                directories.add(output);
            } else if (output.equals(root) || directories.contains(output) || !files.add(output)) {
                throw new IOException("duplicate or conflicting ZIP entry > " + entry.getName());
            }
            for (Path parent = output.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent()) {
                if (files.contains(parent)) { throw new IOException("ZIP file/directory collision > " + entry.getName()); }
                directories.add(parent);
            }
        }
    }

    private static Path entryPath(Path root, ZipEntry entry) throws IOException {
        Path output = root.resolve(entry.getName().replace('\\', '/')).normalize();
        if (!output.startsWith(root)) {
            throw new IOException("ZIP entry escapes extraction directory > " + entry.getName());
        }
        return output;
    }
}
