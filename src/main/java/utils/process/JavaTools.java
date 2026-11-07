package utils.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JavaTools { //locates java & javac in JDK running autograder
    private JavaTools() {}

    public static Path getCompilerPath() {
        return findTool("javac");
    }

    public static Path getRuntimePath() {
        return findTool("java");
    }

    public static Path getDisassemblerPath() {
        return findTool("javap");
    }

    private static Path findTool(String toolName) { //find tool in java.home/bin & check file exists
        String javaHome = System.getProperty("java.home"); //running jdk folder
        String executableName = toolName;

        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            executableName = toolName + ".exe";
        }

        Path executable;

        try {
            executable = Path.of(javaHome, "bin", executableName).toRealPath();
        } catch (IOException error) {
            throw new ProcessExecutionException("Unable to find " + toolName, error);
        }

        if (!Files.isRegularFile(executable)) {
            throw new ProcessExecutionException("Invalid file " + toolName + " at " + executable.toString());
        }

        return executable;
    }
}
