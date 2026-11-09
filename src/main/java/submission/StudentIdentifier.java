package submission;

import java.nio.file.Path;
import java.util.List;

import utils.strings.CommonPatternChecker;

public class StudentIdentifier { //matches ZIP filenames to roster usernames without regard to case
    public StudentDetails identify(Path zipFile, List<StudentDetails> roster) {
        String username = SubmissionFileNames.username(zipFile);

        return findStudent(username, roster);
    }

    public StudentDetails identifyFolder(Path answerRoot, List<StudentDetails> roster) {
        String username = answerRoot.getFileName().toString();
        if (!CommonPatternChecker.ofUsernamePattern(username)) return null;

        StudentDetails student = findStudent(username, roster);
        return student == null ? new StudentDetails("", username, username) : student;
    }

    private StudentDetails findStudent(String username, List<StudentDetails> roster) {
        for (StudentDetails student : roster) {
            if (student.getUsername().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
