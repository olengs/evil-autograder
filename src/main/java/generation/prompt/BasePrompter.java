package generation.prompt;

public final class BasePrompter implements Prompter { //the default contract every generated tester must follow
    public static final String ID = "base";

    private final String instructions;

    // the rules live in tester-prompt.json under base.instructions > they come from the real tester files
    public BasePrompter(PromptCatalog catalog) {
        this.instructions = catalog.getBaseInstructions();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String instructions() {
        return instructions;
    }
}
