package grading;

import exceptions.WorkspacePreparationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import model.QuestionTestPack;
import utils.FileUtils;

public class GradingWorkspace implements AutoCloseable {
    private Path workspaceRoot;
    private static final AtomicLong AUTO_ID = new AtomicLong();

    public GradingWorkspace() {
        createWorkspaceRoot();
    }

    public void Prepare(QuestionTestPack definitions) {
        /// TODO: Currently assumes that student's attempt files don't modify the given files. 
        /// Requires maybe input or validation to get what the files that requires students to change.
        /// TODO: Convert to multithreaded,use CopyDirectorySafe.
        try {
            FileUtils.CopyDirectory(definitions.getQuestionDefinition().getFolderPath(), workspaceRoot, FileUtils.CopyOptions.REPLACE_EXISTING);
            FileUtils.CopyDirectory(definitions.getQuestionAttempt().getAttempRootPath(), workspaceRoot, FileUtils.CopyOptions.REPLACE_EXISTING);
            FileUtils.CopyDirectory(definitions.getTesterDefinition().getFullPath(), workspaceRoot, FileUtils.CopyOptions.REPLACE_EXISTING);
        } catch (Exception e) {
            // Self-clean the partially-created workspace, then rethrow the original (coded) failure.
            throw new WorkspacePreparationException("wsp-002", e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.workspaceRoot != null)
            FileUtils.DeleteFolder(workspaceRoot);
    }

    private void createWorkspaceRoot() {
        Path root;
        try {
            // TODO: Change 05d to value based of config, log(num students). Currently assume max 100000 students.
            root = Files.createTempDirectory("autograder-ws-" + String.format("%05d", AUTO_ID.incrementAndGet()) + "-");
        } catch (Exception e) {
            throw new WorkspacePreparationException("wsp-001", "Temporary workspace cannot be created", e);
        }

        this.workspaceRoot = root;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }
}