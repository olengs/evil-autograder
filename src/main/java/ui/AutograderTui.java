package ui;

import config.Config;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import progress.GradingProgress;
import verification.JUnitGradingRun;

public class AutograderTui { //manages terminal interface while grading runs in background
    //@1ejun6 > creates shared progress state & grading task > updates screen > handles keyboard input > waits for cleanup before closing

    public void run(Config config) throws Exception {
        GradingProgress progress = new GradingProgress();
        ProgressView view = new ProgressView(config.getInputDirectory(), progress.getSnapshot());
        var task = new FutureTask<>(() -> JUnitGradingRun.run(config, progress));
        Thread worker = new Thread(task, "worker"); //@1ejun6 > lambda > execute > store grades > task object
        ResourceUsage resources = new ResourceUsage();

        //@1ejun6 > tamboui check progress every 250ms on its render thread
        TuiConfig terminalConfig = TuiConfig.builder().tickRate(Duration.ofMillis(250)).build();
        try (TuiRunner terminal = TuiRunner.create(terminalConfig)) {
            try {
                worker.start(); //@1ejun6 > starts background thread
                terminal.run((event, runner) -> { //@1ejun6 > event loop
                    //@1ejun6 > redraw only when progress \ elapsed time changes

                    //@1ejun6 > on each tick > read latest grading progress > redraw if changed
                    if (event instanceof TickEvent) {
                        var snapshot = progress.getSnapshot();
                        boolean progressChanged = view.update(snapshot);
                        boolean resourcesChanged = view.updateResources(resources.sample());
                        return progressChanged || resourcesChanged;
                    }
                    //@1ejun6 > ignore non keyboard events > otherwise store event as key
                    if (!(event instanceof KeyEvent key)) return false;

                    if (key.isChar('q') || key.code() == KeyCode.ENTER) {
                        runner.quit();
                    }

                    return false; //ignore other keys
                }, view::render);
            } finally {
                //@1ejun6 > cancel unfinished grading > wait for worker cleanup before closing
                if (!task.isDone()) task.cancel(true);
                awaitWorker(worker, task);
            }
        }

        try {
            task.get(); //propagate grading failures after cleanup
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Exception error) throw error;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private void awaitWorker(Thread worker, FutureTask<?> task) throws InterruptedException {
        boolean interrupted = false;
        while (worker.isAlive()) {
            try {
                worker.join(); //@1ejun6 > finish process & workspace cleanup even if terminal shutdown is interrupted
            } catch (InterruptedException error) {
                task.cancel(true);
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrupted while waiting for grading cleanup");
        }
    }
}
