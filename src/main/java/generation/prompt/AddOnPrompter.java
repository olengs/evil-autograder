package generation.prompt;

// one optional instruction block toggled on or off per run > description is a short label a ui can list
public record AddOnPrompter(String id, String description, String instructions) {
    public AddOnPrompter {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("add-on prompter id is required");
        }
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("add-on prompter instructions are required for " + id);
        }
        description = description == null ? "" : description;
    }

    @Override
    public String toString() {
        return id + " (" + description + ")";
    }
}
