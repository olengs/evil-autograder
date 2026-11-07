//stores immutable junit counts & current check & recent failure details
//shares verification progress with terminal status & summary panels

package grading.progress;

import java.util.List;

public record JUnitSnapshot(boolean enabled, long passed, long failed, long skipped, long aborted, String currentTest, List<String> failures) {
    public static final JUnitSnapshot DISABLED = new JUnitSnapshot(false, 0, 0, 0, 0, "", List.of());

    public JUnitSnapshot {
        failures = List.copyOf(failures);
    }

    public String summary() {
        return passed + " passed · " + failed + " failed · " + skipped + " skipped · " + aborted + " aborted";
    }
}
