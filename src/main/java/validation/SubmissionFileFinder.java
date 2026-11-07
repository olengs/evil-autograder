package validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class SubmissionFileFinder { //finds required files in a students submission > checking both exact names & names with different capital letters

    public Optional<Path> findExact(Path directory, String requiredFileName) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(requiredFileName)).findFirst();
        }
    }

    public Optional<Path> findIgnoringCase(Path directory, String requiredFileName) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(directory)) {

            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(requiredFileName)).limit(2).toList();
            if (matches.size() > 1) {
                throw new IOException("ambiguous filename > " + requiredFileName + " in " + directory);
            }
            return matches.stream().findFirst();
        }
    }

    //Prefer an exact filename; retain a unique case-insensitive candidate when capitalization differs.
    public Optional<Path> findRequired(Path directory, String requiredFileName) throws IOException {
        Optional<Path> exact = findExact(directory, requiredFileName);
        return exact.isPresent() ? exact : findIgnoringCase(directory, requiredFileName);
    }

    public List<Path> findSubmissionFiles(Path directory, List<String> requiredFiles) throws IOException {
        List<Path> submittedFiles = new ArrayList<>();
        for (String requiredFile : requiredFiles) {
            findRequired(directory, requiredFile)
                    .ifPresent(file -> submittedFiles.add(file.toAbsolutePath().normalize()));
        }
        return submittedFiles;
    }
}
