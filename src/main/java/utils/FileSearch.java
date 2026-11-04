package utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FileSearch { //reads directory entries & indexes files with optional per-submission caching
    private static final int maxSearchDepth = 4;
    private final boolean reuseLookups;
    private final Map<Path, List<Path>> directoryEntries = new HashMap<>();
    private final Map<Path, List<Path>> fileIndexes = new HashMap<>();

    FileSearch(boolean reuseLookups) {
        this.reuseLookups = reuseLookups;
    }

    //@1ejun6 > resolve spelling without renaming anything in the extracted submission
    Optional<Path> findFile(Path directory, String name, boolean ignoreCase) throws IOException {
        Path relative = Path.of(name).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("invalid relative submission path > " + name);
        }
        Path current = directory;
        for (Path component : relative) {
            Optional<Path> entry = findEntry(current, component.toString(), ignoreCase);
            if (entry.isEmpty()) return Optional.empty();
            current = entry.get();
        }
        return Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) ? Optional.of(current) : Optional.empty();
    }

    Optional<Path> findDirectory(Path root, String name) throws IOException {
        Optional<Path> folder = findEntry(root, name, true);
        return folder.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
    }

    private Optional<Path> findEntry(Path directory, String name, boolean ignoreCase) throws IOException {
        List<Path> matches = listEntries(directory).stream().filter(path -> ignoreCase
                ? path.getFileName().toString().equalsIgnoreCase(name)
                : path.getFileName().toString().equals(name)).limit(2).toList();
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

    //only build the recursive index if a direct lookup failed
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
