//locates question folders & answer files without changing an extracted submission
//prefers expected folder & accepts unambiguous relocated answers & can cache per-submission lookups

package submission.files;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubmissionFileFinder {
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

    //reuse lookups across rules for this submission only > including failed lookups
    public SubmissionFileFinder forSubmission() {
        return new SubmissionFileFinder(true);
    }

    public Optional<Path> findFileIgnoringCase(Path directory, String requiredFileName) throws IOException {
        return fileSearch.findFile(directory, requiredFileName);
    }

    public Optional<Path> findQuestionFolder(Path root, String expectedFolderName) throws IOException {
        return fileSearch.findDirectory(root, expectedFolderName);
    }

    //prefer expected folder > a relocated answer is accepted only when exactly one match exists
    public Optional<Path> findAnswerFile(Path root, String expectedFolderName, String requiredFile) throws IOException {
        if (!reuseLookups) return locateAnswer(root, expectedFolderName, requiredFile);
        AnswerKey key = new AnswerKey(root.toAbsolutePath().normalize(), expectedFolderName, requiredFile);
        AnswerLookup lookup = answers.get(key);

        if (lookup == null) {
            try {
                lookup = new AnswerLookup(locateAnswer(root, expectedFolderName, requiredFile), null);
            } catch (IOException error) {
                lookup = new AnswerLookup(Optional.empty(), error);
            }
            answers.put(key, lookup);
        }

        if (lookup.error() != null) throw lookup.error();
        return lookup.answer();
    }

    private Optional<Path> locateAnswer(Path root, String expectedFolderName, String requiredFile) throws IOException {
        return findAnswerFile(root, findQuestionFolder(root, expectedFolderName), requiredFile);
    }

    //reuses a resolved question folder > keeps the same direct lookup & fallback search
    public Optional<Path> findAnswerFile(Path root, Optional<Path> folder, String requiredFile) throws IOException {
        if (folder.isPresent()) {
            Optional<Path> answer = findFileIgnoringCase(folder.get(), requiredFile);
            if (answer.isPresent()) return answer;
        }

        List<Path> matches = fileSearch.indexFiles(root).stream().filter(path -> matchesRequiredPath(path, requiredFile)).limit(2).toList();
        if (matches.size() > 1) throw new IOException("ambiguous answer file > " + requiredFile);
        return matches.stream().findFirst();
    }

    public boolean matchesRequiredPath(Path path, String requiredFile) {
        Path required = Path.of(requiredFile).normalize();
        if (required.isAbsolute() || required.startsWith("..") || path.getNameCount() < required.getNameCount()) return false;
        
        for (int i = 1; i <= required.getNameCount(); i++) {
            if (!path.getName(path.getNameCount() - i).toString() .equalsIgnoreCase(required.getName(required.getNameCount() - i).toString())) return false;
        }
        return true;
    }

    public List<Path> findAnswerFiles(Path root, String expectedFolderName, List<String> requiredAnswerFiles) throws IOException {
        List<Path> found = new ArrayList<>();
        for (String required : requiredAnswerFiles) {
            findAnswerFile(root, expectedFolderName, required).ifPresent(path -> found.add(path.toAbsolutePath().normalize()));
        }
        return found;
    }
}
