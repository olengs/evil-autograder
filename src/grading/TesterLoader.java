package grading;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import model.TesterDefinition;

public class TesterLoader {
    public static Map<String, TesterDefinition> LoadTesterFiles(String path) throws IOException {
        Path folderPath = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(folderPath)) {
            throw new IOException(String.format("%s not found", folderPath));
        }

        HashMap<String, TesterDefinition> testers = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folderPath, "*Tester.java")) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    String name = file.getFileName().toString();
                    String id = name.substring(0, name.length() - "Tester.java".length()).toLowerCase();
                    // TODO (iter2): Validate file input.
                    String entry = name.substring(0, name.lastIndexOf('.'));
                    testers.put(id, new TesterDefinition(id, folderPath, folderPath.relativize(file), entry));
                }
            }
        }
        return testers;
    }
}
