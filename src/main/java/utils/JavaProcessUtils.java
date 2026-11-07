package utils;

import exceptions.ProcessExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import model.ProcessResult;

public class JavaProcessUtils { //compiles and runs Java workspaces using the current JDK
    public static ProcessResult compile(Path workspaceDirectory, Duration timeout) throws IOException {
        Path classes = Files.createDirectories(workspaceDirectory.resolve("classes"));
        List<String> command = new ArrayList<>();
        command.add(JavaProcessUtils.getCompilerPath().toString());
        command.add("-d");
        command.add(classes.toString());
        command.add("-cp");
        command.add(workspaceDirectory.toString());

        List<Path> sources;
        //find & sort java files directly inside workspace
        try (var files = Files.list(workspaceDirectory)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IOException("No Java sources in " + workspaceDirectory);
        }
        //each source path is one argument > supports filenames with spaces
        for (Path source : sources) {
            command.add(source.toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, timeout, "prc-001", "javac could not be started");
    }

    public static ProcessResult run(Path workspaceDirectory, String mainClass, Duration timeout) {
        //include compiled classes & workspace resources > separator depends on OS
        String classpath = workspaceDirectory.resolve("classes") + java.io.File.pathSeparator + workspaceDirectory;
        ProcessBuilder builder = new ProcessBuilder(JavaProcessUtils.getRuntimePath().toString(), "-cp", classpath, mainClass);
        builder.directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, timeout, "prc-002", "java execution failed");
    }

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
        String javaHome = System.getProperty("java.home");
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
