package generation.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import config.Config;

public final class PrompterRegistry { //the add-on prompters defined in tester-prompt.json & how to resolve the active ones
    // add a new add-on by adding one entry to addOns in tester-prompt.json > config toggle key is prompter.<id>.enabled

    // preserve declaration order so prompts are assembled deterministically
    private final Map<String, AddOnPrompter> addOns = new LinkedHashMap<>();

    // build the registry straight from config > reads execution.timeout.seconds for {{timeoutSeconds}}
    public PrompterRegistry(Config config) {
        this(config.getExecutionTimeout().toSeconds());
    }

    public PrompterRegistry(long testCaseTimeoutSeconds) {
        this(PromptCatalog.loadDefault(), testCaseTimeoutSeconds);
    }

    public PrompterRegistry(PromptCatalog catalog, long testCaseTimeoutSeconds) {
        // clamp to at least one second so the generated Future.get timeout is always valid
        Map<String, String> values = Map.of("timeoutSeconds", String.valueOf(Math.max(1, testCaseTimeoutSeconds)));
        for (PromptCatalog.AddOn addOn : catalog.getAddOns()) {
            String instructions = PromptCatalog.render(PromptCatalog.join(addOn.instructions()), values);
            register(new AddOnPrompter(addOn.id(), addOn.description(), instructions));
        }
    }


    private void register(AddOnPrompter prompter) {
        addOns.put(prompter.id(), prompter);
    }

    // every known built-in add-on > ui can list these as toggles
    public List<AddOnPrompter> getAllAddOns() {
        return List.copyOf(addOns.values());
    }

    public boolean isKnown(String id) {
        return addOns.containsKey(id);
    }

    // resolve active add-ons from an explicit id list > unknown ids are ignored
    public List<AddOnPrompter> resolve(List<String> enabledIds) {
        List<AddOnPrompter> active = new ArrayList<>();
        if (enabledIds == null) {
            return active;
        }
        // keep registry order rather than caller order so output is stable
        for (AddOnPrompter addOn : addOns.values()) {
            if (enabledIds.contains(addOn.id())) {
                active.add(addOn);
            }
        }
        return active;
    }

    // resolve active add-ons straight from config toggles > prompter.<id>.enabled
    public List<AddOnPrompter> resolveFromConfig(Config config) {
        List<String> enabled = new ArrayList<>();
        for (String id : addOns.keySet()) {
            if (config.isPrompterEnabled(id)) {
                enabled.add(id);
            }
        }
        return resolve(enabled);
    }
}
