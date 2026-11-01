package generation.prompt;

public interface Prompter { //one reusable block of instructions that shapes the tester the llm writes
    // short stable identifier > used as the config toggle key for add-ons
    String id();

    // instruction text contributed to the assembled prompt
    String instructions();
}
