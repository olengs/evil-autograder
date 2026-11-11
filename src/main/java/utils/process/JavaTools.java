package utils.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import utils.files.FileUtils;

/// locates java, javac, javap in JDK running autograder
public final class JavaTools { 
    private JavaTools() {}

    public static Path getCompilerPath() {
        return findTool("javac");
    }

    public static Path getRuntimePath() {
        return findTool("java");
    }

    public static ProcessResult compile(Path workspaceDirectory, Path classesDirectory, Duration timeout) throws IOException {
        return compile(workspaceDirectory, classesDirectory, timeout, ProcessExecutor.DEFAULT_OUTPUT_MAX_BYTES);
    }

    public static ProcessResult compile(Path workspaceDirectory, Path classesDirectory, Duration timeout, long outputMaxBytes) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(JavaTools.getCompilerPath().toString());
        command.add("-d");
        command.add(classesDirectory.toString());
        command.add("-cp");
        command.add(workspaceDirectory.toString());

        List<Path> sources;
        //workspace's direct java files are compilation unit > nested data stays as resources
        try (var files = FileUtils.streamRegularFiles(workspaceDirectory)) {
            sources = files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IOException("No Java sources in " + workspaceDirectory);
        }
        for (Path source : sources) {
            command.add(source.toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, timeout, "javac could not be started", outputMaxBytes);
    }

    public static ProcessResult run(Path workspaceDirectory, Path classesDirectory, String mainClassName, Duration timeout, long outputMaxBytes) {
        //include compiled classes & workspace resources > use current os's classpath separator
        String classpath = classesDirectory + java.io.File.pathSeparator + workspaceDirectory;
        ProcessBuilder builder = new ProcessBuilder(JavaTools.getRuntimePath().toString(), "-cp", classpath, mainClassName);
        builder.directory(workspaceDirectory.toFile());
        return ProcessExecutor.run(builder, timeout, "java execution failed", outputMaxBytes);
    }

    public static Path getDisassemblerPath() {
        return findTool("javap");
    }

    private static Path findTool(String toolName) {
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
