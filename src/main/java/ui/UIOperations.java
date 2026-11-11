package ui;

import java.util.List;

import config.Config;
import generation.GenerationListener;
import generation.QuestionGenerationOutcome;
import grading.progress.GradingProgress;

/// Interface for UI operations, can be overwritten to create more operations
public interface UIOperations {
        List<QuestionGenerationOutcome> generate(Config config, GenerationListener listener) throws Exception;
        void grade(Config config, GradingProgress progress) throws Exception;
}
