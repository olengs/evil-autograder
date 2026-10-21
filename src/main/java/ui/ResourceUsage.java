package ui;

import java.lang.management.ManagementFactory;
import java.util.Locale;

final class ResourceUsage { //samples CPU, heap & thread usage for terminal display
    private final com.sun.management.OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
    private final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private long lastSample;
    record Sample(String summary, int threads) {}
    private Sample sample = new Sample("measuring", 0);

    Sample sample() {
        long now = System.nanoTime();
        if (now - lastSample < 1_000_000_000L) return sample;
        double load = os == null ? -1 : os.getProcessCpuLoad();
        String cpu = load < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", load * 100);

        Runtime runtime = Runtime.getRuntime();
        long heapBytes = runtime.totalMemory() - runtime.freeMemory();
        long totalMemory = os == null ? -1 : os.getTotalMemorySize();

        String heapPercent = totalMemory > 0 ? String.format(Locale.ROOT, "%.1f%%", 100.0 * heapBytes / totalMemory) : "n/a";
        String summary = "cpu " + cpu + " | heap " + heapBytes / (1024 * 1024) + " MiB · " + heapPercent + " ram";
        sample = new Sample(summary, threads.getThreadCount());
        lastSample = now;
        return sample;
    }
}
