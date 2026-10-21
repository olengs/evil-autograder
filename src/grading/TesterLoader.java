package grading;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import model.TesterDefinition;

public class TesterLoader {
    public static List<TesterDefinition> LoadTesterFiles(String path) throws IOException {
        Path folderPath = Paths.get(path);
        if (!Files.isDirectory(folderPath)) {
            throw new IOException(String.format("%s not found", folderPath));
        }

        List<TesterDefinition> testers = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folderPath, "*Tester.java")) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    String name = file.getFileName().toString();
                    String id = name.substring(0, name.length() - "Tester.java".length());
                    testers.add(new TesterDefinition(id, file.toString()));
                }
            }
        }
        return testers;
    }
}
