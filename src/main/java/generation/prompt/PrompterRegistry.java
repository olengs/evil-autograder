package generation.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import config.Config;

public final class PrompterRegistry { //the add-on prompters defined in tester-prompt.json & how to resolve the active ones
    // add a new add-on by adding one entry to addOns in tester-prompt.json > config toggle key is prompter.<id>.enabled

    // preserve declaration order so prompts are assembled deterministically
    private final Map<String, AddOnPrompter> addOns = new LinkedHashMap<>();

    public PrompterRegistry() {
        this(PromptCatalog.loadDefault());
    }

    public PrompterRegistry(PromptCatalog catalog) {
        for (PromptCatalog.AddOn addOn : catalog.getAddOns()) {
            addOns.put(addOn.id(), new AddOnPrompter(addOn.id(), addOn.description(), PromptCatalog.join(addOn.instructions())));
        }
    }

    // every known add-on > ui can list these as toggles
    public List<AddOnPrompter> getAllAddOns() {
        return List.copyOf(addOns.values());
    }

    public boolean isKnown(String id) {
        return addOns.containsKey(id);
    }

    // resolve active add-ons from an explicit id list > unknown ids are ignored > registry order keeps output stable
    public List<AddOnPrompter> resolve(List<String> enabledIds) {
        if (enabledIds == null) {
            return List.of();
        }
        return addOns.values().stream().filter(addOn -> enabledIds.contains(addOn.id())).toList();
    }

    // resolve active add-ons straight from config toggles > prompter.<id>.enabled
    public List<AddOnPrompter> resolveFromConfig(Config config) {
        return addOns.values().stream().filter(addOn -> config.isPrompterEnabled(addOn.id())).toList();
    }
}
