package validation;

import java.io.IOException;
import utils.FileUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class SubmissionFileFinder { //finds required files in a students submission > checking both exact names & names with different capital letters

    public Optional<Path> findExact(Path directory, String requiredFileName) throws IOException { //@1ejun6 > a path \ nothing
        Path expected = FileUtils.resolveContained(directory, requiredFileName);
        directory = expected.getParent();
        requiredFileName = expected.getFileName().toString();
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        final String name = requiredFileName;
        try (Stream<Path> files = FileUtils.listFiles(directory).stream()) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(name)).findFirst();
        }
    }

    public Optional<Path> findIgnoringCase(Path directory, String requiredFileName) throws IOException {
        Path expected = FileUtils.resolveContained(directory, requiredFileName);
        directory = expected.getParent();
        requiredFileName = expected.getFileName().toString();
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        final String name = requiredFileName;
        try (Stream<Path> files = FileUtils.listFiles(directory).stream()) {

            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).limit(2).toList();
            if (matches.size() > 1) {
                throw new IOException("ambiguous filename > " + requiredFileName + " in " + directory);
            }
            return matches.stream().findFirst();
        }
    }

    public List<Path> findSubmissionFiles(Path directory, List<String> requiredFiles) throws IOException {
        List<Path> submittedFiles = new ArrayList<>();

        for (String requiredFile : requiredFiles) {
            Optional<Path> exact = findExact(directory, requiredFile);

            if (exact.isPresent()) {
                submittedFiles.add(exact.get().toAbsolutePath().normalize());
                continue;
            }

            //@1ejun6 > if exact name is absent > keep a case insensitive candidate so we still know what student submitted
            Optional<Path> caseInsensitive = findIgnoringCase(directory, requiredFile);
            if (caseInsensitive.isPresent()) {
                submittedFiles.add(caseInsensitive.get().toAbsolutePath().normalize());
            }
        }
        return submittedFiles;
    }
}
