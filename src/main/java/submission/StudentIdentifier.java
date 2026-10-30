package submission;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import model.StudentDetails;

/** Matches a submitted ZIP filename against every relevant scoresheet record. */
public class StudentIdentifier {
    /** Returns a student only when the scoresheet contains exactly one matching record. */
    public StudentDetails identify(Path zipFile, List<StudentDetails> students) {
        List<StudentDetails> matches = findMatches(zipFile, students);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    /**
     * Keeps duplicate records visible so callers can report ambiguity instead of
     * silently selecting the first student. The optional academic-year prefix is
     * excluded from the username, as it is in the original ZIP matching behavior.
     */
    public List<StudentDetails> findMatches(Path zipFile, List<StudentDetails> students) {
        String fileName = zipFile.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) return List.of();
        String username = fileName.substring(0, fileName.length() - 4)
                .replaceFirst("^\\d{4}-\\d{4}-", "").strip();
        return students.stream()
                .filter(student -> student.getUsername().strip().equalsIgnoreCase(username))
                .toList();
    }
}
