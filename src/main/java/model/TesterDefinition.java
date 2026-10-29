package model;

import java.nio.file.Path;

public class TesterDefinition {
    private final String id;
    private final Path root;
    private final Path path;
    private final String entryPoint;

    public TesterDefinition(String id, Path root, Path path, String entryPoint) {
        this.id = id;
        this.root = root;
        this.path = path;
        this.entryPoint = entryPoint;
    }

    public String getId() {
        return id;
    }

    public Path getRelativePathToRoot() {
        return path;
    }

    public Path getFullPath() {
        return root.resolve(path);
    }

    public String getEntryPoint() {
        return entryPoint;
    }
}
