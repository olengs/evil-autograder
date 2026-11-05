package generation.run;

import java.nio.file.Path;
import java.util.List;

public final class GenerationRequest { //one tester generation run > what to read & how to prompt & where to write
    private final Path examPdf; // exam pdf to read questions from
    private final Path resourcesDirectory; // answer-template folders used to enumerate questions
    private final Path outputDirectory; // target folder for generated tester files
    private final List<String> enabledAddOnPrompters; // add-on prompter ids toggled on for this run
    private final String userPrompt; // free-form extra instructions > empty when none

    public GenerationRequest(Path examPdf, Path resourcesDirectory, Path outputDirectory,
            List<String> enabledAddOnPrompters, String userPrompt) {
        if (examPdf == null) {
            throw new IllegalArgumentException("examPdf is required");
        }
        if (resourcesDirectory == null) {
            throw new IllegalArgumentException("resourcesDirectory is required");
        }
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory is required");
        }
        this.examPdf = examPdf;
        this.resourcesDirectory = resourcesDirectory;
        this.outputDirectory = outputDirectory;
        // copy toggle list so caller cannot mutate it later > empty when null
        this.enabledAddOnPrompters = enabledAddOnPrompters == null ? List.of() : List.copyOf(enabledAddOnPrompters);
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

    public List<String> getEnabledAddOnPrompters() {
        return enabledAddOnPrompters;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    @Override
    public String toString() {
        return "GenerationRequest{examPdf=" + examPdf + ", resourcesDirectory=" + resourcesDirectory
                + ", outputDirectory=" + outputDirectory + ", enabledAddOnPrompters=" + enabledAddOnPrompters
                + ", hasUserPrompt=" + !userPrompt.isBlank() + '}';
    }
}
