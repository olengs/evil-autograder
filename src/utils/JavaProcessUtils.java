package utils;

import exceptions.ProcessExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaProcessUtils {
    
    /** @return absolute path to the {@code javac} executable in the running JDK. Note: Path is validated. */
    public static Path getJavacExecutablePath() {
        return getExecutionTool("javac");
    }

    /** @return absolute path to the {@code java} executable in the running JDK. Note: Path is validated*/
    public static Path getJavaExecutablePath() {
        return getExecutionTool("java");
    }
    
    /** Resolves a JDK tool executable from {@code java.home/bin}. */
    private static Path getExecutionTool(String tool) {
        String home = System.getProperty("java.home"); // finds system directory
        String exe = tool;
        // handle windows, require .exe
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            exe = tool + ".exe";
        }
        Path executable;
        try {
            executable = Path.of(home, "bin", exe).toRealPath();
        } catch (IOException e) {
            throw new ProcessExecutionException("Unable to find" + tool, e);
        }
        
        if (!Files.isRegularFile(executable)) {
            throw new ProcessExecutionException("Invalid file" + tool + " at " + executable.toString());
        }

        return executable;
    }
}
