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
        //TODO > iteration 2 > support multithreaded preparation with safe file copying
        //@1ejun6 > current preparation uses a separate temporary folder per workspace
        //@1ejun6 > multithreaded preparation remains pending
        for (String name : question.getAllowedResourceFiles()) { //@1ejun6 > copy only the declared support files.
            Path destination = directory.resolve(name);
            Files.createDirectories(destination.getParent());
            FileUtils.copyFile(question.getResourceDirectory().resolve(name), destination, FileUtils.CopyOptions.REPLACE_EXISTING);
        }
        //@1ejun6 > keep students helper classes & tester data with their answers > same flow as pair 2
        FileUtils.copyDirectory(attempt.getQuestionDirectory(), directory, FileUtils.CopyOptions.REPLACE_EXISTING);
        for (String name : question.getLockedResourceFiles()) { //put back anything a student changed by accident
            FileUtils.copyFile(question.getResourceDirectory().resolve(name), directory.resolve(name), FileUtils.CopyOptions.REPLACE_EXISTING);
        }
        Path testerFile = tester.getSourceFile(); //@1ejun6 > copy tester as a file not a directory
        FileUtils.copyFile(testerFile, directory.resolve(testerFile.getFileName()), FileUtils.CopyOptions.REPLACE_EXISTING);
    }

    public Path getDirectory() {
        return directory;
    }

    @Override
    public void close() throws IOException {
        FileUtils.deleteDirectory(directory);
    }
}
