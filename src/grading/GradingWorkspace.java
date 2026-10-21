package grading;

import exceptions.WorkspacePreparationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.TesterDefinition;
import utils.FileUtils;

public class GradingWorkspace implements AutoCloseable {
    private String workspaceRoot;
    private String originalPath;
    private static final AtomicLong AUTO_ID = new AtomicLong();

    public GradingWorkspace() {
        createWorkspaceRoot();
    }

    // TODO: Copies the student's files and tester files into a single temp folder
    public void Prepare(QuestionDefinition questionDefinition, QuestionAttempt questionAttempt, 
                TesterDefinition testerDefinition) {
        
        if (questionDefinition == null || questionAttempt == null || testerDefinition == null) {
            throw new IllegalArgumentException("null grading defintions received");
        }

        // TODO: Question definition should have a folder to copy for each question.
        String testerPath = testerDefinition.getPath();
        try {
            // FileUtils.CopyFiles(questionDef, originalPath, FileUtils.CopyOptions.REPLACE_EXISTING);
            // FileUtils.CopyFiles(questionAttempt, originalPath, FileUtils.CopyOptions.REPLACE_EXISTING);
            FileUtils.CopyFiles(testerPath, originalPath, FileUtils.CopyOptions.REPLACE_EXISTING);
        } catch (Exception e) {
            // Self-clean the partially-created workspace, then rethrow the original (coded) failure.
            this.workspaceRoot = null;
            throw new WorkspacePreparationException("wsp-002", e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        FileUtils.DeleteFolder(workspaceRoot);
    }

    private void createWorkspaceRoot() {
        Path root;
        try {
            root = Files.createTempDirectory("autograder-ws-" + String.format("%05d", AUTO_ID.incrementAndGet()) + "-");
        } catch (Exception e) {
            throw new WorkspacePreparationException("wsp-001", "Temporary workspace cannot be created", e);
        }

        this.workspaceRoot = root.toString();
    }

}