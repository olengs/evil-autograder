package ui;

import config.SetupConfig;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.event.TickEvent;
import grading.verification.JUnitRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import grading.progress.GradingProgress;

public class TerminalUi { //runs terminal setup & grading with background work & cancellation cleanup
    //creates shared progress state & grading task > updates screen > handles keyboard input > waits for cleanup before closing

    private GradingView view;
    private IOException setupFailure;

    public void run(Path configFile) throws Exception {
        GradingProgress progress = new GradingProgress();
        SetupConfig setup = new SetupConfig(configFile);
        StringBuilder input = new StringBuilder();
        view = null;
        setupFailure = null;
        var task = new FutureTask<>(() -> JUnitRunner.run(setup.config(), progress));
        Thread worker = new Thread(task, "worker"); //lambda > execute > store grades > task object
        ResourceUsage resources = new ResourceUsage();

        //tamboui check progress every 250ms on its render thread
        TuiConfig terminalConfig = TuiConfig.builder().tickRate(Duration.ofMillis(250)).build();
        try (TuiRunner terminal = TuiRunner.create(terminalConfig)) {
            try {
                if (setup.issue() == null) {
                    view = new GradingView(setup.config().getInputDirectory(), progress.getSnapshot());
                    worker.start();
                } else progress.pauseElapsedTime();
                terminal.run((event, runner) -> { //event loop
                    //redraw only when progress \ elapsed time changes

                    if (event instanceof KeyEvent key && key.isCtrlC()) {
                        runner.quit();
                        return true;
                    }
                    if (view == null) {
                        if (event instanceof PasteEvent paste) {
                            input.append(paste.text());
                            return true;
                        }
                        if (!(event instanceof KeyEvent key)) return false;
                        if (key.code() == KeyCode.ENTER) {
                            progress.resumeElapsedTime();
                            try {
                                setup.replace(input.toString());
                                input.setLength(0);
                                if (setup.issue() == null) {
                                    view = new GradingView(setup.config().getInputDirectory(), progress.getSnapshot());
                                    worker.start();
                                } else progress.pauseElapsedTime();
                            } catch (IOException error) {
                                setupFailure = error;
                                runner.quit();
                            }
                        } else if (key.code() == KeyCode.BACKSPACE && !input.isEmpty()) input.delete(input.offsetByCodePoints(input.length(), -1), input.length());
                        else if (key.code() == KeyCode.CHAR && !Character.isISOControl(key.codePoint())) input.append(key.string());
                        return true;
                    }
                    //on each tick > read latest grading progress > redraw if changed
                    if (event instanceof TickEvent) {
                        var snapshot = progress.getSnapshot();
                        boolean progressChanged = view.update(snapshot);
                        boolean resourcesChanged = view.updateResources(resources.sample());
                        return progressChanged || resourcesChanged;
                    }
                    //ignore non keyboard events > otherwise store event as key
                    if (!(event instanceof KeyEvent key)) return false;

                    if (key.isChar('q')) {
                        runner.quit();
                    }

                    return false; //ignore other keys
                }, frame -> {
                    if (view == null) SetupScreen.draw(frame, setup.issue(), input.toString());
                    else view.render(frame);
                });
            } finally {
                //cancel unfinished grading > wait for worker cleanup before closing
                if (!task.isDone()) task.cancel(true);
                awaitWorker(worker, task);
            }
        } finally {
            progress.finishElapsedTime();
        }

        if (setupFailure != null) throw setupFailure;
        if (view == null) throw new CancellationException("setup interrupted");
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
                worker.join(); //finish process & workspace cleanup even if terminal shutdown is interrupted
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
