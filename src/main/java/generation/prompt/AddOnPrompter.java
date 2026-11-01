package generation.prompt;

public final class AddOnPrompter implements Prompter { //one optional instruction block toggled on or off per run
    private final String id;
    private final String description; // short human label > useful for a ui listing the toggles
    private final String instructions;

    public AddOnPrompter(String id, String description, String instructions) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("add-on prompter id is required");
        }
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("add-on prompter instructions are required for " + id);
        }
        this.id = id;
        this.description = description == null ? "" : description;
        this.instructions = instructions;
    }

    @Override
    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    @Override
    public String instructions() {
        return instructions;
    }

    @Override
    public String toString() {
        return id + " (" + description + ")";
    }
}
