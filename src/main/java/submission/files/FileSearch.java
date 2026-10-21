package submission.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FileSearch { //resolves case-insensitive paths & caches bounded submission file searches
    private static final int maxSearchDepth = 4;
    private final boolean reuseLookups;
    private final Map<Path, List<Path>> directoryEntries = new HashMap<>();
    private final Map<Path, List<Path>> fileIndexes = new HashMap<>();

    FileSearch(boolean reuseLookups) {
        this.reuseLookups = reuseLookups;
    }

    Optional<Path> findFile(Path directory, String name) throws IOException {
        Path relative = Path.of(name).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) throw new IOException("invalid relative submission path > " + name);

        Path current = directory;
        for (Path component : relative) {
            Optional<Path> entry = findEntry(current, component.toString());
            if (entry.isEmpty()) return Optional.empty();
            current = entry.get();
        }
        return Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) ? Optional.of(current) : Optional.empty();
    }

    Optional<Path> findDirectory(Path root, String name) throws IOException {
        Optional<Path> folder = findEntry(root, name);
        return folder.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
    }

    private Optional<Path> findEntry(Path directory, String name) throws IOException {
        List<Path> matches = listEntries(directory).stream().filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).limit(2).toList();
        if (matches.size() > 1) throw new IOException("ambiguous submission path > " + name + " in " + directory);
        return matches.stream().findFirst();
    }

    private List<Path> listEntries(Path directory) throws IOException {
        Path key = directory.toAbsolutePath().normalize();
        if (reuseLookups && directoryEntries.containsKey(key)) return directoryEntries.get(key);

        List<Path> entries = List.of();
        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.list(directory)) {
                entries = paths.toList();
            } catch (UncheckedIOException error) {
                throw error.getCause();
            }
        }

        if (reuseLookups) directoryEntries.put(key, entries);
        return entries;
    }

    //build recursive index only after a direct lookup fails

    List<Path> indexFiles(Path root) throws IOException {
        Path key = root.toAbsolutePath().normalize();
        if (reuseLookups && fileIndexes.containsKey(key)) return fileIndexes.get(key);
        
        List<Path> files = List.of();
        if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(root, maxSearchDepth + 1)) {
                files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).filter(path -> visiblePath(root.relativize(path))).toList();
            } catch (UncheckedIOException error) {
                throw error.getCause();
            }
        }
        if (reuseLookups) fileIndexes.put(key, files);
        return files;
    }

    private boolean visiblePath(Path relative) {
        for (Path component : relative) {
            if (component.toString().startsWith(".")) return false;
        }
        return true;
    }
}
