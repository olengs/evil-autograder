//discovers teacher-provided tester programs & indexes them by lowercase question id
//makes testers available to grading & rejects unreadable files or duplicate tester ids

package assessment;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TesterLoader {
    private TesterLoader() {}

    public static Map<String, TesterDefinition> load(Path testerDirectory) throws IOException {
        Path testerDirectoryPath = testerDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(testerDirectoryPath)) throw new IOException("tester folder not found: " + testerDirectoryPath);

        Map<String, TesterDefinition> testers = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(testerDirectoryPath, "*Tester.java")) {
            for (Path file : files) {
                if (!Files.isRegularFile(file))  continue;

                if (!Files.isReadable(file)) throw new IOException("tester file is not readable > " + file);

                String name = file.getFileName().toString();
                String questionId = name.substring(0, name.length() - "Tester.java".length()).toLowerCase(Locale.ROOT);
                String mainClassName = name.substring(0, name.length() - ".java".length());

                if (questionId.isBlank() || testers.containsKey(questionId)) throw new IOException("invalid / duplicate tester id > " + name);

                testers.put(questionId, new TesterDefinition(questionId, testerDirectoryPath, testerDirectoryPath.relativize(file), mainClassName));
            }
        }
        return testers;
    }
}
