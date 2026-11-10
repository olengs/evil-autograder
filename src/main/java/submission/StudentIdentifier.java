package submission;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

import submission.files.StudentHeaderReader;
import utils.files.FileUtils;
import utils.process.Interruptions;
import utils.strings.CommonPatternChecker;
import validation.SubmissionIssue;

public class StudentIdentifier { //matches ZIP filenames to roster usernames without regard to case
    private final Pattern usernamePattern;

    public StudentIdentifier(Pattern usernamePattern) {
        this.usernamePattern = usernamePattern;
    }

    public StudentDetails identify(Path zipFile, List<StudentDetails> roster) {
        String username = CommonPatternChecker.normalizeUsername(SubmissionFileNames.username(zipFile));

        return usernamePattern.matcher(username).matches() ? findStudent(username, roster) : null;
    }

    public StudentDetails identifyFolder(Path answerRoot, List<StudentDetails> roster) {
        String username = CommonPatternChecker.normalizeUsername(answerRoot.getFileName().toString());
        return usernamePattern.matcher(username).matches() ? resolveCandidate(username, "", username, roster) : null;
    }

    public record HeaderIdentification(StudentDetails student, boolean conflicting) {}

    public HeaderIdentification inspectHeaders(Path answerRoot, List<StudentDetails> roster,
            List<SubmissionIssue> issues, List<String> identifiers, int maxDepth) throws IOException {
        var candidates = new LinkedHashMap<String, StudentDetails>();
        try (var files = FileUtils.streamPaths(answerRoot, maxDepth)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                Interruptions.check("header identification interrupted");
                StudentHeaderReader.Header header;
                try {
                    header = StudentHeaderReader.read(file);
                } catch (IOException error) {
                    if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
                    issues.add(SubmissionIssue.warning("header read error", "unable to read header > "
                            + answerRoot.relativize(file) + " > " + error.getMessage(), null));
                    continue;
                }
                if (header.name() != null && !header.name().isBlank()) identifiers.add(header.name());
                if (header.email() == null || header.email().isBlank()) continue;
                identifiers.add(header.email());
                String username = CommonPatternChecker.normalizeUsername(header.email().split("@", 2)[0]);
                if (username.isBlank()) continue;
                StudentDetails student = resolveCandidate(username, header.name() == null ? "" : header.name(), header.email().strip(), roster);
                if (student != null) candidates.putIfAbsent(username, student);
            }
        }
        //Conflicting email headers cannot establish a single student identity.
        return new HeaderIdentification(candidates.size() == 1 ? candidates.values().iterator().next() : null,
                candidates.size() > 1);
    }

    private StudentDetails findStudent(String username, List<StudentDetails> roster) {
        for (StudentDetails student : roster) {
            if (CommonPatternChecker.normalizeUsername(student.getUsername()).equals(username)) {
                return student;
            }
        }
        return null;
    }

    private StudentDetails resolveCandidate(String username, String name, String email, List<StudentDetails> roster) {
        StudentDetails student = findStudent(username, roster);
        return student == null ? new StudentDetails(name, username, email) : student;
    }

}
