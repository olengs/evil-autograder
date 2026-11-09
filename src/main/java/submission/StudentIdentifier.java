package submission;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import submission.files.StudentHeaderReader;
import utils.process.Interruptions;
import utils.strings.CommonPatternChecker;
import validation.SubmissionIssue;

public class StudentIdentifier { //matches ZIP filenames to roster usernames without regard to case
    public StudentDetails identify(Path zipFile, List<StudentDetails> roster) {
        String username = normalizeUsername(SubmissionFileNames.username(zipFile));

        return findStudent(username, roster);
    }

    public StudentDetails identifyFolder(Path answerRoot, List<StudentDetails> roster) {
        String username = normalizeUsername(answerRoot.getFileName().toString());
        return resolveCandidate(username, "", username, roster);
    }

    public StudentDetails identifyHeaders(Path answerRoot, List<StudentDetails> roster, List<SubmissionIssue> issues) throws IOException {
        var candidates = new LinkedHashMap<String, StudentDetails>();
        try (var files = Files.walk(answerRoot, 6)) {
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
                if (header.email() == null || header.email().isBlank()) continue;
                String username = normalizeUsername(header.email().split("@", 2)[0]);
                StudentDetails student = resolveCandidate(username, header.name() == null ? "" : header.name(), header.email().strip(), roster);
                if (student != null) candidates.putIfAbsent(username, student);
            }
        }
        //Conflicting email headers cannot establish a single student identity.
        return candidates.size() == 1 ? candidates.values().iterator().next() : null;
    }

    private StudentDetails findStudent(String username, List<StudentDetails> roster) {
        if (!CommonPatternChecker.ofUsernamePattern(username)) return null;
        for (StudentDetails student : roster) {
            if (normalizeUsername(student.getUsername()).equals(username)) {
                return student;
            }
        }
        return null;
    }

    private StudentDetails resolveCandidate(String username, String name, String email, List<StudentDetails> roster) {
        if (!CommonPatternChecker.ofUsernamePattern(username)) return null;
        StudentDetails student = findStudent(username, roster);
        return student == null ? new StudentDetails(name, username, email) : student;
    }

    private String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

}
