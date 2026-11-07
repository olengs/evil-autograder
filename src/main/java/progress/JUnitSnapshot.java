package progress;

import java.util.List;

//small & immutable junit status for tui
public record JUnitSnapshot(boolean enabled, long passed, long failed, long skipped, long aborted, String currentTest, List<String> failures) {
    public static final JUnitSnapshot DISABLED = new JUnitSnapshot(false, 0, 0, 0, 0, "", List.of());

    public JUnitSnapshot {
        failures = List.copyOf(failures);
    }

    public String summary() {
        return passed + " passed · " + failed + " failed · " + skipped + " skipped · " + aborted + " aborted";
    }
}
