package submission.files;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// locates question folders & unambiguous answer files
public class SubmissionFileFinder { 
    private final FileSearch fileSearch;
    private final int maxSearchDepth;

    public SubmissionFileFinder() {
        this(5);
    }

    public SubmissionFileFinder(int maxSearchDepth) {
        this(false, maxSearchDepth);
    }

    private SubmissionFileFinder(boolean reuseLookups, int maxSearchDepth) {
        this.maxSearchDepth = maxSearchDepth;
        this.fileSearch = new FileSearch(reuseLookups, maxSearchDepth);
    }

    //reuse directory entries & file indexes for this submission only
    public SubmissionFileFinder forSubmission() {
        return new SubmissionFileFinder(true, maxSearchDepth);
    }

    public Optional<Path> findFileIgnoringCase(Path directory, String requiredFileName) throws IOException {
        return fileSearch.findFile(directory, requiredFileName);
    }

    public Optional<Path> findQuestionFolder(Path root, String expectedFolderName) throws IOException {
        return fileSearch.findDirectory(root, expectedFolderName);
    }

    //prefer resolved question folder > accept a relocated answer only when unambiguous
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
