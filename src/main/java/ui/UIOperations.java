package ui;

import java.util.List;
import config.Config;
import generation.GenerationListener;
import generation.QuestionGenerationOutcome;
import grading.progress.GradingProgress;

public interface UIOperations {
        List<QuestionGenerationOutcome> generate(Config config, GenerationListener listener) throws Exception;
        void grade(Config config, GradingProgress progress) throws Exception;
}
