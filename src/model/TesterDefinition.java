package model;

import java.nio.file.Path;

public class TesterDefinition {
    private final String id;
    private final Path root;
    private final Path path;

    public TesterDefinition(String id, Path root, Path path) {
        this.id = id;
        this.root = root;
        this.path = path;
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
}
