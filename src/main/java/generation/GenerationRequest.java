package generation;

import java.nio.file.Path;

public final class GenerationRequest { //input paths, output folder & optional instructions for one run
    private final Path examPdf;
    private final Path resourcesDirectory;
    private final Path outputDirectory;
    private final String userPrompt;

    public GenerationRequest(Path examPdf, Path resourcesDirectory, Path outputDirectory, String userPrompt) {
        if (examPdf == null) throw new IllegalArgumentException("examPdf is required");
        if (resourcesDirectory == null) throw new IllegalArgumentException("resourcesDirectory is required");
        if (outputDirectory == null) throw new IllegalArgumentException("outputDirectory is required");
        
        this.examPdf = examPdf;
        this.resourcesDirectory = resourcesDirectory;
        this.outputDirectory = outputDirectory;
        this.userPrompt = userPrompt == null ? "" : userPrompt.strip();
    }

    public Path getExamPdf() {
        return examPdf;
    }

    public Path getResourcesDirectory() {
        return resourcesDirectory;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public String getUserPrompt() {
        return userPrompt;
    }
}
