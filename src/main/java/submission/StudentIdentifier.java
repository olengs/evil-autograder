package submission;

import java.nio.file.Path;
import java.util.List;

public class StudentIdentifier { //matches ZIP filenames to roster usernames without regard to case
    public StudentDetails identify(Path zipFile, List<StudentDetails> roster) {
        String username = SubmissionFileNames.username(zipFile);

        for (StudentDetails student : roster) {
            if (student.getUsername().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
