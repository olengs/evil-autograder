package assessment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TesterLoader { //discovers testers & indexes them by question ID
    private TesterLoader() {}

    public static Map<String, TesterDefinition> load(Path testerDirectory) throws IOException {
        Path testerDirectoryPath = testerDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(testerDirectoryPath)) throw new IOException("tester folder not found: " + testerDirectoryPath);

        var marks = TesterMetadata.readMarks(testerDirectoryPath);
        Map<String, TesterDefinition> testers = new HashMap<>();
        try (var files = Files.walk(testerDirectoryPath, 2)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Tester.java")).sorted().toList()) {
                if (!Files.isRegularFile(file))  continue;

                if (!Files.isReadable(file)) throw new IOException("tester file is not readable > " + file);

                String name = file.getFileName().toString();
                String questionId = name.substring(0, name.length() - "Tester.java".length()).toLowerCase(Locale.ROOT);
                String mainClassName = name.substring(0, name.length() - ".java".length());

                if (questionId.isBlank() || testers.containsKey(questionId)) throw new IOException("invalid / duplicate tester id > " + name);

                Double allocation = marks.get(questionId);
                if (allocation == null) throw new IOException("marks.txt has no allocation for " + questionId);
                int caseCount = TesterMetadata.loadCaseCount(testerDirectoryPath,
                        name.substring(0, name.length() - "Tester.java".length()), file);
                testers.put(questionId, new TesterDefinition(testerDirectoryPath,
                        testerDirectoryPath.relativize(file), mainClassName, allocation, caseCount));
            }
        }
        return testers;
    }
}
