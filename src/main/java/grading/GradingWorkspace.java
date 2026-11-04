package grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.TesterDefinition;
import utils.FileUtils;

public class GradingWorkspace implements AutoCloseable { //creates & cleans up temporary folder used to grade one question
    private final Path directory;

    public GradingWorkspace() throws IOException {
        directory = Files.createTempDirectory("autograder-ws-"); //@1ejun6 > give this question its own temporary folder
    }

    public void prepare(QuestionDefinition question, QuestionAttempt attempt,
            TesterDefinition tester) throws IOException {
        //@1ejun6 > current preparation uses a separate temporary folder per workspace
        for (String name : question.getAllowedResourceFiles()) { //@1ejun6 > copy only the declared support files.
            Path destination = directory.resolve(name);
            Files.createDirectories(destination.getParent());
            FileUtils.copyFile(question.getResourceDirectory().resolve(name), destination, FileUtils.CopyOptions.REPLACE_EXISTING);
        }
        //TODO > iteration 2 > validate which files students may change so answers cannot overwrite given files
        //@1ejun6 > overwrite validation remains pending > student files currently replace matching support files
        //@1ejun6 > keep students helper classes & tester data with their answers > same flow as pair 2
        FileUtils.copyDirectory(attempt.getQuestionDirectory(), directory, FileUtils.CopyOptions.REPLACE_EXISTING);
        copyTesterResources(question, tester);
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
                FileUtils.copyFile(source, destination, FileUtils.CopyOptions.REPLACE_EXISTING);
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
