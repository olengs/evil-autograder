package ui;

import assessment.AssessmentLoader;
import config.Config;
import config.SetupConfig;
import config.SetupConfig.TesterSource;
import openai.OpenAiClient;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.event.TickEvent;
import generation.QuestionGenerationOutcome;
import generation.QuestionGenerationOutcome.GenerationStatus;
import generation.TesterGenerator;
import grading.progress.GradingProgress;
import grading.verification.JUnitRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import utils.process.Interruptions;

public class TerminalUi {
    private enum Screen { source, setup, generationForm, generating, generationReview, grading, cancelling }

    private Screen screen;
    private SetupConfig setup;
    private final StringBuilder input = new StringBuilder();
    private String instructions = "";
    private String alert = "";
    private GenerationView generation;
    private GradingView grading;
    private GradingProgress progress;
    private final ResourceUsage resources = new ResourceUsage();
    private FutureTask<?> task;
    private FutureTask<List<QuestionGenerationOutcome>> generationTask;
    private Thread worker;
    private boolean taskObserved;
    private boolean cancelled;
    private boolean cancellingGeneration;
    private volatile boolean gradingStarted;
    private Throwable gradingFailure;

    public void run(Path configFile) throws Exception {
        if (active()) throw new IllegalStateException("terminal UI is already running");
        task = null;
        worker = null;
        progress = null;
        generation = null;
        grading = null;
        gradingFailure = null;
        cancelled = false;
        instructions = "";
        alert = "";
        input.setLength(0);
        setup = new SetupConfig(configFile);
        screen = Screen.source;
        var terminalConfig = TuiConfig.builder().tickRate(Duration.ofMillis(250)).build();
        try (TuiRunner terminal = TuiRunner.create(terminalConfig)) {
            try {
                terminal.run(this::handle, this::render);
            } finally {
                if (active() && !task.isDone()) task.cancel(true);
                awaitWorker();
            }
        } finally {
            if (progress != null) progress.finishElapsedTime();
        }
        observeTask();
        if (cancelled || task == null) throw new CancellationException("run cancelled");
        if (gradingFailure instanceof Exception error) throw error;
        if (gradingFailure instanceof Error error) throw error;
        if (screen == Screen.generationReview && !generation.canGrade()) throw new IOException("tester generation incomplete");
    }

    private boolean handle(Event event, TuiRunner terminal) {
        if (event instanceof KeyEvent key && key.isCtrlC()) {
            close(terminal);
            return true;
        }
        if (event instanceof TickEvent) {
            if (screen == Screen.cancelling && !active()) terminal.quit();
            else observeTask();
            if (screen == Screen.grading) {
                grading.update(progress.getSnapshot());
                grading.updateResources(resources.sample());
            }
            return true;
        }
        if (screen == Screen.cancelling) return false;
        if (event instanceof PasteEvent paste) {
            if (screen == Screen.setup) input.append(paste.text());
            if (screen == Screen.generationForm) generation.append(paste.text());
            return true;
        }
        if (!(event instanceof KeyEvent key)) return false;
        try {
            handleKey(key, terminal);
        } catch (IOException | IllegalArgumentException error) {
            alert = error.getMessage();
            if (screen == Screen.generationForm) generation.formError(alert);
            else if (screen == Screen.generationReview) generation.fail(alert);
            else if (screen == Screen.setup && setup.issue() == null) screen = Screen.source;
        }
        return true;
    }

    private void handleKey(KeyEvent key, TuiRunner terminal) throws IOException {
        switch (screen) {
            case source -> {
                if (key.isChar('1') || key.isChar('2')) {
                    setup.selectSource(key.isChar('1') ? TesterSource.existing : TesterSource.generated);
                    alert = "";
                    input.setLength(0);
                    continueSetup();
                } else if (key.isChar('q') || key.code() == KeyCode.ESCAPE) close(terminal);
            }
            case setup -> {
                if (key.code() == KeyCode.ESCAPE) screen = Screen.source;
                else if (key.code() == KeyCode.ENTER) {
                    setup.replace(input.toString());
                    alert = "";
                    input.setLength(0);
                    continueSetup();
                } else if (key.code() == KeyCode.BACKSPACE && !input.isEmpty()) {
                    input.delete(input.offsetByCodePoints(input.length(), -1), input.length());
                } else if (key.code() == KeyCode.CHAR && !Character.isISOControl(key.codePoint())) input.append(key.string());
            }
            case generationForm -> {
                if (key.code() == KeyCode.ESCAPE) {
                    if (generation.editing()) generation.finishEditing();
                    else screen = Screen.source;
                } else if (key.code() == KeyCode.ENTER && generation.editing()) generation.finishEditing();
                else if (key.code() == KeyCode.ENTER && !active()) {
                    OpenAiClient.readApiKey(setup.config().getLlmApiKeyEnv());
                    instructions = generation.instructions();
                    setup.generationPaths(generation.pdfPath(), generation.outputPath());
                    if (setup.issue() != null) { input.setLength(0); screen = Screen.setup; }
                    else { generation.paths(setup.config()); startGeneration(); }
                } else generation.edit(key);
            }
            case generating -> {
                if (key.isChar('q')) close(terminal);
                else scroll(key);
            }
            case generationReview -> {
                if (key.code() == KeyCode.ENTER && generation.canGrade()) startGrading();
                else if (key.isChar('r')) {
                    // Regeneration is an explicit action and goes through the form again.
                    instructions = generation.instructions();
                    setup.selectSource(TesterSource.generated);
                    continueSetup();
                } else if (key.isChar('b') || key.code() == KeyCode.ESCAPE) screen = Screen.source;
                else if (key.isChar('q')) close(terminal);
                else scroll(key);
            }
            case grading -> { if (key.isChar('q')) close(terminal); }
            case cancelling -> { }
        }
    }

    private void continueSetup() throws IOException {
        if (setup.issue() != null) { screen = Screen.setup; return; }
        if (setup.testerSource() == TesterSource.existing) startGrading();
        else {
            generation = new GenerationView(setup.config(), instructions);
            screen = Screen.generationForm;
        }
    }

    private void startGeneration() throws IOException {
        if (active()) return;
        Config config = setup.config();
        generation.begin();
        screen = Screen.generating;
        GenerationView listener = generation;
        String userPrompt = generation.instructions();
        generationTask = new FutureTask<>(() -> new TesterGenerator(config).generate(userPrompt, listener));
        startTask(generationTask, "tester-generation");
    }

    private void startGrading() throws IOException {
        if (active()) return;
        Config config = setup.configForGrading();
        boolean generated = setup.testerSource() == TesterSource.generated;
        List<QuestionGenerationOutcome> outcomes = generated ? generation.results() : List.of();
        progress = new GradingProgress();
        grading = new GradingView(config.getInputDirectory(), progress.getSnapshot());
        gradingStarted = false;
        screen = Screen.grading;
        startTask(new FutureTask<>(() -> {
            try {
                if (generated) verifyGeneratedTesters(config, outcomes);
                Interruptions.check("grading interrupted");
                gradingStarted = true;
                return JUnitRunner.run(config, progress);
            } catch (Exception | Error error) {
                progress.failed(error.getMessage() == null ? "grading failed" : error.getMessage());
                throw error;
            } finally { progress.finishElapsedTime(); }
        }), "grading");
    }

    private static void verifyGeneratedTesters(Config config, List<QuestionGenerationOutcome> outcomes) throws IOException {
        var assessment = AssessmentLoader.load(config.getResourcesDirectory());
        if (outcomes.size() != assessment.getQuestions().size()) throw new IOException("question list changed > regenerate testers");
        for (var question : assessment.getQuestions()) {
            Interruptions.check("grading interrupted");
            Path file = config.getTestersDirectory().resolve(question.getId() + "Tester.java").toAbsolutePath().normalize();
            boolean written = outcomes.stream().anyMatch(outcome -> outcome.questionId().equals(question.getId())
                    && outcome.status() == GenerationStatus.written && outcome.writtenFile() != null
                    && outcome.writtenFile().toAbsolutePath().normalize().equals(file));
            if (!written || !Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new IOException("generated tester is missing or unreadable > " + file);
            }
        }
    }

    private void startTask(FutureTask<?> next, String name) {
        if (active()) throw new IllegalStateException("a worker is already running");
        task = next;
        taskObserved = false;
        gradingFailure = null;
        worker = new Thread(next, name);
        worker.start();
    }

    private boolean active() { return worker != null && worker.isAlive(); }

    private void observeTask() {
        if (task == null || taskObserved || active() || !task.isDone() || screen == Screen.cancelling) return;
        taskObserved = true;
        try {
            if (screen == Screen.generating) {
                generation.finish(generationTask.get(), "");
                screen = Screen.generationReview;
            } else task.get();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            if (screen == Screen.generating || (screen == Screen.grading && !gradingStarted && setup.testerSource() == TesterSource.generated)) {
                generation.fail(message);
                screen = Screen.generationReview;
            } else gradingFailure = cause;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancelled = true;
        } catch (CancellationException error) { cancelled = true; }
    }

    private void scroll(KeyEvent key) {
        if (key.code() == KeyCode.UP) generation.scroll(-1);
        if (key.code() == KeyCode.DOWN) generation.scroll(1);
        if (key.code() == KeyCode.PAGE_UP) generation.scroll(-8);
        if (key.code() == KeyCode.PAGE_DOWN) generation.scroll(8);
    }

    private void close(TuiRunner terminal) {
        if (screen == Screen.cancelling) return;
        if (active()) {
            cancellingGeneration = screen == Screen.generating;
            cancelled = !task.isDone();
            if (!task.isDone()) task.cancel(true);
            screen = Screen.cancelling;
        } else {
            cancelled = screen == Screen.source || screen == Screen.setup || screen == Screen.generationForm;
            terminal.quit();
        }
    }

    private void render(Frame frame) {
        switch (screen) {
            case source -> SetupScreen.drawChoice(frame, alert);
            case setup -> {
                var issue = setup.issue();
                if (!alert.isEmpty()) issue = new SetupConfig.Issue(issue.setting(), issue.label(), issue.path(), alert);
                SetupScreen.draw(frame, issue, input.toString());
            }
            case generationForm -> generation.render(frame, true, false, resources.sample());
            case generating, generationReview -> generation.render(frame, false, false, resources.sample());
            case grading -> grading.render(frame);
            case cancelling -> {
                if (cancellingGeneration) generation.render(frame, false, true, resources.sample());
                else SetupScreen.drawMessage(frame, "cancelling", "waiting for grading processes and workspace cleanup");
            }
        }
    }

    private void awaitWorker() throws InterruptedException {
        boolean interrupted = false;
        while (active()) {
            try { worker.join(); }
            catch (InterruptedException error) { task.cancel(true); interrupted = true; }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrupted while waiting for cleanup");
        }
    }
}
