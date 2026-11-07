package ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.GradeStatus;
import progress.GradingProgress;

final class JunitRun { //runs junit once & forwards grading events to ui
    private volatile Process process;
    private volatile boolean cancelled;
    private volatile int mavenThreads = -1;

    int mavenThreads() { return mavenThreads; }

    void run(Path configFile, GradingProgress progress) throws Exception {
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            command.add("cmd");
            command.add("/c");
        }
        command.add("mvn");
        command.add("test");
        command.add("-Dautograder.progress=true");
        if (configFile != null)
            command.add("-Dautograder.config=" + configFile);
        boolean completed = false;
        Path events = java.nio.file.Files.createTempFile("autograder-progress-", ".jsonl");
        command.add("-Dautograder.progress.file=" + events.toAbsolutePath());
        Path logFile = Path.of("log", "junit.log");
        try {
            if (cancelled)
                throw new InterruptedException("grading cancelled");
            java.nio.file.Files.createDirectories(logFile.getParent());
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            if (cancelled) {
                cancel();
                throw new InterruptedException("grading cancelled");
            }
            try (var reader = new java.io.RandomAccessFile(events.toFile(), "r")) {
                while (true) {
                    String line;
                    long position;
                    while (true) {
                        position = reader.getFilePointer();
                        line = reader.readLine();
                        if (line == null)
                            break;
                        long end = reader.getFilePointer();
                        reader.seek(end - 1);
                        int lastByte = reader.read();
                        if (lastByte != '\n') {
                            reader.seek(position);
                            break;
                        }
                        String json = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        JsonArray event = JsonParser.parseString(json).getAsJsonArray();
                        updateThreadCount(event);
                        String name = event.get(0).getAsString();
                        if (name.equals("batchFinished")) {
                            completed = true;
                            progress.preparing("checking junit results");
                        } else if (!name.equals("failed")) {
                            apply(progress, event);
                        }
                    }
                    if (process.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                        break;
                }
                // Read any final events written just before Maven exited.
                String line;
                while ((line = reader.readLine()) != null) {
                    String json = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    JsonArray event = JsonParser.parseString(json).getAsJsonArray();
                    updateThreadCount(event);
                    String name = event.get(0).getAsString();
                    if (name.equals("batchFinished"))
                        completed = true;
                    else if (!name.equals("failed"))
                        apply(progress, event);
                }
            }
            int result = process.exitValue();
            if (cancelled)
                throw new InterruptedException("grading cancelled");
            if (result != 0 || !completed) {
                throw new IOException(result != 0 ? "junit tests failed > see log/junit.log"
                        : "junit did not report a completed grading run > see log/junit.log");
            }
            progress.batchFinished();
        } catch (Exception error) {
            Process running = process;
            if (running != null && running.isAlive()) {
                running.descendants().forEach(ProcessHandle::destroyForcibly);
                running.destroyForcibly();
            }
            if (!cancelled)
                progress.failed(error.getMessage() == null ? error.toString() : error.getMessage());
            throw error;
        } finally {
            process = null;
            mavenThreads = 0;
            java.nio.file.Files.deleteIfExists(events);
        }
    }

    private void updateThreadCount(JsonArray event) {
        var metadata = event.get(event.size() - 1);
        if (metadata.isJsonObject() && metadata.getAsJsonObject().has("mavenThreads")) {
            mavenThreads = metadata.getAsJsonObject().get("mavenThreads").getAsInt();
        }
    }

    void cancel() {
        cancelled = true;
        Process running = process;
        if (running != null) {
            running.descendants().forEach(ProcessHandle::destroyForcibly);
            running.destroyForcibly();
        }
    }

    private void apply(GradingProgress progress, JsonArray e) {
        String name = e.get(0).getAsString();
        switch (name) {
            case "preparing" -> progress.preparing(e.get(1).getAsString());
            case "validation" -> progress.validationStarted(e.get(1).getAsString());
            case "batch" -> {
                List<String> questions = new ArrayList<>();
                e.get(2).getAsJsonArray().forEach(q -> questions.add(q.getAsString()));
                progress.batchStarted(e.get(1).getAsInt(), questions);
            }
            case "submission" -> progress.submissionStarted(e.get(1).getAsString());
            case "question" -> progress.questionStarted(e.get(1).getAsString());
            case "compilation" ->
                progress.compilationStarted(e.get(1).getAsString(), Duration.ofMillis(e.get(2).getAsLong()));
            case "execution" ->
                progress.executionStarted(e.get(1).getAsString(), Duration.ofMillis(e.get(2).getAsLong()));
            case "questionFinished" ->
                progress.questionFinished(e.get(1).getAsString(), GradeStatus.valueOf(e.get(2).getAsString()));
            case "submissionFinished" -> progress.submissionFinished();
            default -> throw new IllegalArgumentException("unknown progress event: " + name);
        }
    }
}
