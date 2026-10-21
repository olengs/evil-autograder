package autograder.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class AnswerFileLocator {

    public Optional<Path> findExact(Path directory, String requiredFileName) throws IOException { //a path ||| nothing
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(directory)) {

            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(requiredFileName))
                    .findFirst();
        }
    }

    public Optional<Path> findIgnoringCase(Path directory, String requiredFileName) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(directory)) {

            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(requiredFileName))
                    .limit(2).toList();
            if (matches.size() > 1) {
                throw new IOException("ambiguous filename > " + requiredFileName + " in " + directory);
            }
            return matches.stream().findFirst();
        }
    }

    public List<Path> locateAnswerFiles(Path directory, List<String> requiredFiles) throws IOException {
        List<Path> answerFiles = new ArrayList<>();

        for (String requiredFile : requiredFiles) {
            Optional<Path> exact = findExact(directory, requiredFile);

            if (exact.isPresent()) {
                answerFiles.add(exact.get());
                continue;
            }

            //if exact name is absent > keep a case insensitive candidate so we still know what student submitted

            Optional<Path> caseInsensitive = findIgnoringCase(directory, requiredFile);
            caseInsensitive.ifPresent(answerFiles::add);
        }
        return answerFiles;
    }
}
