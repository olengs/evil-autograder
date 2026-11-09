package assessment;

import java.nio.file.Path;

public class TesterDefinition { //stores a tester's source path & executable main class
    private final double maximumMarks;
    private final int numTestCases;
    private final Path testerDirectory;
    private final Path relativePath;
    private final String mainClassName;

    public TesterDefinition(Path testerDirectory, Path relativePath, String mainClassName, double maximumMarks, int numTestCases) {
        this.maximumMarks = maximumMarks;
        this.numTestCases = numTestCases;
        this.testerDirectory = testerDirectory;
        this.relativePath = relativePath;
        this.mainClassName = mainClassName;
    }

    public double getMaximumMarks() { return maximumMarks; }

    /** Zero means no declared case count (legacy tester). */
    public int getNumTestCases() { return numTestCases; }

    public Path getTesterDirectory() { return testerDirectory; }

    public Path getSourceFile() {
        return testerDirectory.resolve(relativePath);
    }

    public String getMainClassName() {
        return mainClassName;
    }
}
