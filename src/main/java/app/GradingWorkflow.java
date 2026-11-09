package app;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import config.Config;
import grading.QuestionGrade;
import grading.progress.GradingProgress;
import report.ScoreSheetWriter;
import submission.StudentSubmission;
import utils.process.Interruptions;

/** Runs grading and exports results without depending on a test framework. */
public final class GradingWorkflow {
    private GradingWorkflow() {}

    public static Map<StudentSubmission, List<QuestionGrade>> run(Config config, GradingProgress progress) throws IOException {
        try {
            Interruptions.check("grading interrupted");
            var results = Autograder.run(config, progress);
            Interruptions.check("grading interrupted before export");
            progress.preparing("saving scoresheet");
            ScoreSheetWriter.write(results,
                    config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName()));
            progress.completed();
            return results;
        } catch (IOException | RuntimeException | Error failure) {
            progress.failed(failure.getMessage() == null ? failure.toString() : failure.getMessage());
            throw failure;
        } finally {
            progress.finishElapsedTime();
        }
    }
}
