//locates question folders & answer files without changing an extracted submission
//prefers expected folder & accepts unambiguous relocated answers & can cache per-submission lookups

package submission.files;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class SubmissionFileFinder {
    private final FileSearch fileSearch;

    public SubmissionFileFinder() {
        this(false);
    }

    private SubmissionFileFinder(boolean reuseLookups) {
        this.fileSearch = new FileSearch(reuseLookups);
    }

    //reuse directory entries & file indexes for this submission only
    public SubmissionFileFinder forSubmission() {
        return new SubmissionFileFinder(true);
    }

    public Optional<Path> findFileIgnoringCase(Path directory, String requiredFileName) throws IOException {
        return fileSearch.findFile(directory, requiredFileName);
    }

    public Optional<Path> findQuestionFolder(Path root, String expectedFolderName) throws IOException {
        return fileSearch.findDirectory(root, expectedFolderName);
    }

    //prefer the resolved question folder > accept a relocated answer only when unambiguous
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
}
