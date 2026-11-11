package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import assessment.AssessmentLoader;
import assessment.TesterLoader;
import generation.QuestionSpec;
import openai.OpenAiClient;
import submission.SubmissionArchives;
import utils.files.PdfFileUtils;

/// Checking the configurations before allowing certain actions
public final class OperationChecks {
    private OperationChecks() {}

    public static void generation(Config config) throws IOException {
        config.validateGenerationSettings();
        OpenAiClient.readApiKey(config.getLlmApiKeyEnv()); // Check presence without displaying or sending the key.
        PdfFileUtils.requirePdf(config.getExamPdfFile());
        QuestionSpec.collect(config.getResourcesDirectory());
        writable(config.getGenerationOutputDirectory());
    }

    public static void grading(Config config) throws IOException {
        var assessment = AssessmentLoader.load(config.getResourcesDirectory());
        var testers = TesterLoader.load(config.getTestersDirectory());
        if (testers.isEmpty()) throw new IOException("no testers found > generate testers first or check testers.folder");
        // Missing individual testers retain the grader's normal skipped-question behaviour.
        if (assessment.getQuestions().isEmpty()) throw new IOException("no assessment questions found");
        if (SubmissionArchives.listZipFiles(config.getSubmissionsDirectory()).isEmpty())
            throw new IOException("no submission ZIP files found");
        writable(config.getOutputDirectory());
    }

    private static void writable(Path path) throws IOException {
        Path parent = path.toAbsolutePath();
        while (parent != null && !Files.exists(parent)) parent = parent.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent))
            throw new IOException("output directory is not writable > " + path);
    }
}
