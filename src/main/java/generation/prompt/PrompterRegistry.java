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

    // the per-test-case timeout used by the timeout-resistance add-on > comes from execution.timeout.seconds
    private final long testCaseTimeoutSeconds;

    // build the registry straight from config > reads execution.timeout.seconds for the timeout add-on
    public PrompterRegistry(Config config) {
        this(config.getExecutionTimeout().toSeconds());
    }

    public PrompterRegistry(long testCaseTimeoutSeconds) {
        // clamp to at least one second so the generated Future.get timeout is always valid
        this.testCaseTimeoutSeconds = Math.max(1, testCaseTimeoutSeconds);
        register(new AddOnPrompter(TIMEOUT_RESISTANT,
                "detect student infinite loops with a per-test-case timeout",
                // TODO idk abt this, need actual testing + introduce new thread
                """
                TIMEOUT RESISTANCE (detect student infinite loops):
                - A student method may contain an infinite loop. The tester must deliberately detect this and
                  treat it as a failed test case, not hang forever.
                - Run the student method call for EACH test case on a separate thread and bound how long you wait:
                    import java.util.concurrent.*;
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    try {
                        Future<ResultType> future = executor.submit(() -> methodUnderTest(inputs));
                        ResultType result = future.get(%d, TimeUnit.SECONDS); // timeout from execution.timeout.seconds
                        // compare result to expected, then print Passed or Failed
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        System.out.println("Failed -> timeout");
                    } catch (Exception e) {
                        System.out.println("Failed -> Exception");
                    } finally {
                        executor.shutdownNow();
                    }
                - Use a timeout of %d seconds for the Future.get(...) call.
                - A timed-out test case must print exactly one outcome line: "Failed -> timeout".
                - Keep each test case independent so one hung call never blocks the others.
                """.formatted(this.testCaseTimeoutSeconds, this.testCaseTimeoutSeconds)));
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
