package utils;

import exceptions.ProcessExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaProcessUtils { //finds java & javac belonging to jdk running grader
    public static Path getCompilerPath() { //return validated javac path
        return findTool("javac");
    }

    public static Path getRuntimePath() { //return validated java path
        return findTool("java");
    }

    private static Path findTool(String toolName) { //find tool in java.home/bin & check file exists
        String javaHome = System.getProperty("java.home"); //running jdk folder
        String executableName = toolName;
        
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            executableName = toolName + ".exe"; //add .exe for windows
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
