package ui;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import progress.ProgressSnapshot;

final class ResourceUsage { //cpu & heap & threads
    private final com.sun.management.OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
    private final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private long lastSample;
    private long lastRevision = -1;
    private String text = "jvm > measuring";

    String sample(Thread ui, Thread worker, ProgressSnapshot snapshot) {
        long now = System.nanoTime();
        if (now - lastSample < 1_000_000_000L && snapshot.revision() == lastRevision) return text;
        double load = os == null ? -1 : os.getProcessCpuLoad();
        String cpu = load < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", load * 100);

        Runtime runtime = Runtime.getRuntime();
        long heapBytes = runtime.totalMemory() - runtime.freeMemory();
        long totalMemory = os == null ? -1 : os.getTotalMemorySize();

        String heapPercent = totalMemory > 0 ? String.format(Locale.ROOT, "%.1f%%", 100.0 * heapBytes / totalMemory) : "n/a";
        var infos = threads.getThreadInfo(threads.getAllThreadIds());
        int uiCount = 0, workerCount = 0, otherCount = 0;
        for (var info : infos) {
            if (info == null) continue;
            if (info.getThreadId() == ui.threadId()) uiCount++;
            else if (info.getThreadId() == worker.threadId()) workerCount++;
            else otherCount++;
        }
        boolean cleanup = snapshot.isFinished() || snapshot.message().equals("cleaning up temporary files");
        int prepare = !cleanup && !snapshot.totalsKnown() ? workerCount : 0;
        int grading = !cleanup && snapshot.totalsKnown() ? workerCount : 0;
        int results = cleanup ? workerCount : 0;
        text = "jvm > cpu " + cpu + " | heap " + heapBytes / (1024 * 1024) + " MiB · " + heapPercent + " ram"
                + "\nthreads > ui  prepare & validate  compile & test  results & cleanup  background"
                + String.format(Locale.ROOT, "\n          %-4d%-20d%-16d%-19d%d", uiCount, prepare, grading, results, otherCount);
        lastSample = now;
        lastRevision = snapshot.revision();
        return text;
    }
}
