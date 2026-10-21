package model;

public class TesterDefinition {
    private final String id;
    private final String path;

    public TesterDefinition(String id, String path) {
        this.id = id;
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }    
}
