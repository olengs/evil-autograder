// Locates question folders and answer files for validation and grading preparation.
// Supports exact and case-insensitive searches with optional per-submission caching.

package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubmissionFileFinder { //shared read-only answer lookup for validation rules & grading workspace preparation
    private final boolean reuseLookups;
    private final FileSearch fileSearch;
    private final Map<AnswerKey, AnswerLookup> answers = new HashMap<>();
    private record AnswerKey(Path root, String folder, String file) {}
    private record AnswerLookup(Optional<Path> answer, IOException error) {}

    public SubmissionFileFinder() {
        this(false);
    }

    private SubmissionFileFinder(boolean reuseLookups) {
        this.reuseLookups = reuseLookups;
        this.fileSearch = new FileSearch(reuseLookups);
    }

    //@1ejun6 > one validation owns this lookup > reuse results across rules never across submissions
    public SubmissionFileFinder forSubmission() {
        return new SubmissionFileFinder(true);
    }

    public Optional<Path> findExact(Path directory, String requiredFileName) throws IOException { //@1ejun6 > a path \ nothing
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        return fileSearch.findFile(directory, requiredFileName, false);
    }

    public Optional<Path> findIgnoringCase(Path directory, String requiredFileName) throws IOException {
        return fileSearch.findFile(directory, requiredFileName, true);
    }

    public Optional<Path> findQuestionFolder(Path root, String expectedFolder) throws IOException {
        return fileSearch.findDirectory(root, expectedFolder);
    }

    //prefer the declared question folder > otherwise accept only one unambiguous relocated answer
    public Optional<Path> findAnswerFile(Path root, String expectedFolder, String requiredFile) throws IOException {
        if (!reuseLookups) return locateAnswer(root, expectedFolder, requiredFile);
        AnswerKey key = new AnswerKey(root.toAbsolutePath().normalize(), expectedFolder, requiredFile);
        AnswerLookup lookup = answers.get(key);
        if (lookup == null) {
            try {
                lookup = new AnswerLookup(locateAnswer(root, expectedFolder, requiredFile), null);
            } catch (IOException error) {
                lookup = new AnswerLookup(Optional.empty(), error);
            }
            answers.put(key, lookup);
        }
        if (lookup.error() != null) throw lookup.error();
        return lookup.answer();
    }

    private Optional<Path> locateAnswer(Path root, String expectedFolder, String requiredFile) throws IOException {
        Optional<Path> folder = findQuestionFolder(root, expectedFolder);
        if (folder.isPresent()) {
            Optional<Path> answer = findIgnoringCase(folder.get(), requiredFile);
            if (answer.isPresent()) return answer;
        }
        List<Path> matches = fileSearch.indexFiles(root).stream()
                .filter(path -> matchesRequiredPath(path, requiredFile)).limit(2).toList();
        if (matches.size() > 1) throw new IOException("ambiguous answer file > " + requiredFile);
        return matches.stream().findFirst();
    }

    public boolean matchesRequiredPath(Path path, String requiredFile) {
        Path required = Path.of(requiredFile).normalize();
        if (required.isAbsolute() || required.startsWith("..") || path.getNameCount() < required.getNameCount()) return false;
        for (int i = 1; i <= required.getNameCount(); i++) {
            if (!path.getName(path.getNameCount() - i).toString()
                    .equalsIgnoreCase(required.getName(required.getNameCount() - i).toString())) return false;
        }
        return true;
    }

    public List<Path> findAnswerFiles(Path root, String expectedFolder, List<String> requiredFiles) throws IOException {
        List<Path> found = new ArrayList<>();
        for (String required : requiredFiles) {
            findAnswerFile(root, expectedFolder, required).ifPresent(path -> found.add(path.toAbsolutePath().normalize()));
        }
        return found;
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
