package grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.TesterDefinition;
import utils.FileUtils;
import utils.SubmissionFileFinder;

public class GradingWorkspace implements AutoCloseable { //creates & cleans up temporary folder used to grade one question
    private final Path directory;

    public GradingWorkspace() throws IOException {
        directory = Files.createTempDirectory("autograder-ws-"); //@1ejun6 > give this question its own temporary folder
    }

    public void prepare(QuestionDefinition question, QuestionAttempt attempt,
            TesterDefinition tester) throws IOException {
        var finder = new SubmissionFileFinder();
        Map<String, Path> answers = locateRequiredAnswers(question, attempt, finder);
        List<Path> folders = submissionFolders(attempt, answers);
        for (String name : question.getAllowedResourceFiles()) {
            Path source = question.getResourceDirectory().resolve(name);
            //declared Java helpers may be student work (e.g. ShapeComparator); data & bytecode stay supplied
            if (name.endsWith(".java")) {
                List<Path> helpers = new ArrayList<>();
                for (Path folder : folders) {
                    finder.findIgnoringCase(folder, name).ifPresent(helpers::add);
                }
                helpers = helpers.stream().distinct().toList();
                if (helpers.size() > 1) throw new IOException("ambiguous support file > " + name);
                if (!helpers.isEmpty()) source = helpers.getFirst();
            }
            copyFile(source, name);
        }
        //normalize only destination names > extracted folders, filenames & source contents stay untouched
        for (var answer : answers.entrySet()) copyFile(answer.getValue(), answer.getKey());
        copySubmissionData(folders);
        copyTesterResources(question, tester);
    }

    //@1ejun6 > question folder & relocated answer folders > reuse for helpers & data
    private List<Path> submissionFolders(QuestionAttempt attempt, Map<String, Path> answers) throws IOException {
        List<Path> folders = new ArrayList<>();
        if (Files.isDirectory(attempt.getQuestionDirectory())) {
            folders.add(attempt.getQuestionDirectory().toRealPath());
        }
        for (Path answer : answers.values()) folders.add(answer.getParent().toRealPath());
        return folders.stream().distinct().toList();
    }

    //@1ejun6 > restore missing data files > keep supplied resources & copy selected java answers separately
    private void copySubmissionData(List<Path> folders) throws IOException {
        for (Path folder : folders) {
            try (var files = Files.walk(folder)) {
                for (Path source : files.filter(Files::isRegularFile).toList()) {
                    String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.startsWith(".") || name.endsWith(".java") || name.endsWith(".class")) continue;
                    String relativeName = folder.relativize(source).toString();
                    if (!Files.exists(directory.resolve(relativeName))) copyFile(source, relativeName);
                }
            }
        }
    }

    private Map<String, Path> locateRequiredAnswers(QuestionDefinition question, QuestionAttempt attempt,
            SubmissionFileFinder finder) throws IOException {
        Map<String, Path> answers = new LinkedHashMap<>();
        for (String required : question.getRequiredFiles()) {
            List<Path> matches = attempt.getSubmittedFiles().stream()
                    .filter(path -> finder.matchesRequiredPath(path, required)).distinct().toList();
            if (matches.size() > 1) throw new IOException("ambiguous answer file > " + required);
            Path source = matches.isEmpty() ? finder.findIgnoringCase(attempt.getQuestionDirectory(), required)
                    .orElseThrow(() -> new IOException("missing required answer > " + required)) : matches.getFirst();
            answers.put(required, source);
        }
        return answers;
    }

    private void copyFile(Path source, String relativeName) throws IOException {
        Path destination = directory.resolve(relativeName).normalize();
        if (!destination.startsWith(directory) || destination.equals(directory)) {
            throw new IOException("invalid workspace path > " + relativeName);
        }
        Files.createDirectories(destination.getParent());
        //workspace owns directory creation > shared helper validates & replaces the file
        FileUtils.copyFileReplacing(source, destination);
    }

    private void copyTesterResources(QuestionDefinition question, TesterDefinition tester) throws IOException {
        Path testerFile = tester.getSourceFile();
        Path testerFolder = testerFile.getParent();
        try (var entries = Files.walk(testerFolder)) {
            for (Path source : entries.toList()) {
                Path relativePath = testerFolder.relativize(source);
                Path destination = directory.resolve(relativePath);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                    continue;
                }
                //only this tester belongs in workspace > keep shared helpers & data
                if (source.getFileName().toString().endsWith("Tester.java") && !source.equals(testerFile)) {
                    continue;
                }
                //a tester side answer template must not replace the student required answer
                if (question.getRequiredFiles().stream().anyMatch(name ->
                        Path.of(name).normalize().toString().equalsIgnoreCase(relativePath.toString()))) {
                    continue;
                }
                // Tester resources retain their existing replacement priority after the exclusions above.
                FileUtils.copyFileReplacing(source, destination);
            }
        }
    }

    public Path getDirectory() {
        return directory;
    }

    @Override
    public void close() throws IOException {
        FileUtils.deleteDirectory(directory);
    }
}
