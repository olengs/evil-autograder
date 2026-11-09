package ui;

import java.util.List;

import app.GradingWorkflow;
import config.Config;
import config.OperationChecks;
import generation.GenerationListener;
import generation.QuestionGenerationOutcome;
import generation.TesterGenerator;
import grading.progress.GradingProgress;

public class UiActiveOperations implements UIOperations {
    public List<QuestionGenerationOutcome> generate(Config config, GenerationListener listener) throws Exception {
        OperationChecks.generation(config);
        return new TesterGenerator(config).generate("", listener);
    }
    public void grade(Config config, GradingProgress progress) throws Exception {
        OperationChecks.grading(config);
        GradingWorkflow.run(config, progress);
    }
}
