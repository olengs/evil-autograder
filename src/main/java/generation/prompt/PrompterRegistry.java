package generation.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import config.Config;

public final class PrompterRegistry { //the fixed built-in set of add-on prompters & how to resolve the active ones
    // add a new built-in by adding one entry here > config toggle key is prompter.<id>.enabled
    public static final String TIMEOUT_RESISTANT = "timeout-resistant";
    public static final String STRICT_OUTPUT_FORMAT = "strict-output-format";
    public static final String EXTRA_EDGE_CASES = "extra-edge-cases";

    // preserve declaration order so prompts are assembled deterministically
    private final Map<String, AddOnPrompter> addOns = new LinkedHashMap<>();

    public PrompterRegistry() {
        register(new AddOnPrompter(TIMEOUT_RESISTANT, 
                "make test cases timeout resistant",
                """
                TIMEOUT RESISTANCE:
                - A student method may loop forever. Never let one test case block the others.
                - Keep each test case independent; do not share mutable state between cases.
                - Avoid unbounded loops, sleeps, or waiting on input in the tester itself.
                - Prefer small, fast inputs that still exercise the required behaviour.
                """));
        register(new AddOnPrompter(STRICT_OUTPUT_FORMAT,
                "enforce strict grader output format",
                """
                STRICT OUTPUT FORMAT:
                - Emit only the required lines: the "Test <n>:" header, optional Expected/Actual debug lines,
                  and exactly one "Passed" / "Failed" / "Failed -> <reason>" outcome line per case.
                - Do not print banners, totals, timing, or decorative separators that contain the words
                  Test, Passed, or Failed.
                - Compare values precisely; for collections compare using equals, not toString, unless the
                  expected value is defined as a string.
                """));
        register(new AddOnPrompter(EXTRA_EDGE_CASES,
                "add extra edge-case coverage",
                """
                EXTRA EDGE CASES:
                - In addition to the examples in the question, add a few edge cases:
                  empty inputs, single-element inputs, boundary values, and mixed/invalid data where allowed.
                - Keep every added case deterministic with a clearly computed expected value.
                - Do not hardcode outputs; compute expectations from the documented behaviour.
                """));
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
