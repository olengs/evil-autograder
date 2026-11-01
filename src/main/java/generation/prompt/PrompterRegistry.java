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
                """
                TIMEOUT RESISTANCE (detect student infinite loops without hanging the program):
                - A student method may contain an infinite loop. The tester must detect this per test case,
                  mark that case as failed, continue to the remaining cases, and still EXIT at the end.

                CRITICAL JVM FACTS you must account for:
                - future.cancel(true) and shutdownNow() only set the thread's interrupt flag. They CANNOT stop a
                  student thread stuck in a tight loop like `while(true){}` that never checks interruption.
                  That worker thread will keep running in the background after the timeout. Do not assume it dies.
                - A normal (non-daemon) worker thread that is still running will KEEP THE JVM ALIVE, so the
                  program hangs after the last test instead of exiting. You MUST avoid this.

                REQUIRED PATTERN:
                1. Create ONE shared executor that uses DAEMON threads, so leftover runaway threads cannot keep
                   the JVM alive:
                       import java.util.concurrent.*;
                       ExecutorService executor = Executors.newCachedThreadPool(r -> {
                           Thread t = new Thread(r);
                           t.setDaemon(true);   // daemon > JVM can exit even if this thread is still looping
                           return t;
                       });
                2. Submit EACH test case's student call as its own task and bound the wait:
                       Future<ResultType> future = executor.submit(() -> methodUnderTest(inputs));
                       try {
                           ResultType result = future.get(%d, TimeUnit.SECONDS); // per-case timeout
                           // compare result to expected, then print "Passed" or "Failed"
                       } catch (TimeoutException e) {
                           future.cancel(true);            // best-effort only; the thread may keep running
                           System.out.println("Failed -> timeout");
                       } catch (Exception e) {
                           System.out.println("Failed -> Exception");
                       }
                3. Do NOT create a new executor inside each test case, and do NOT call shutdownNow() between cases.
                   A fresh per-case executor plus lingering runaway threads is what makes later cases slow down
                   and also time out. Use the one shared daemon executor for every case.
                4. Build a brand-new, independent input for every test case. Never reuse or mutate an input object
                   from a previous case, so one case's state can never affect the next.
                5. At the very end of main(), after printing the score, FORCE the program to exit so any leftover
                   runaway daemon thread cannot make it hang:
                       executor.shutdownNow();  // best effort
                       System.out.println(score);
                       System.out.flush();
                       System.exit(0);          // guarantees the JVM terminates

                OUTCOME RULES:
                - Use a timeout of %d seconds for each future.get(...) call.
                - A timed-out case prints exactly one outcome line: "Failed -> timeout".
                - Each test case stays fully independent so one hung call never blocks or slows the others.
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
