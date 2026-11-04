// Identifies a submission's student using its ZIP filename and the class roster.
// Removes the optional academic-year prefix before matching the username.

package submission;

import java.nio.file.Path;
import java.util.List;
import model.StudentDetails;

public class StudentIdentifier {
    public StudentDetails identify(Path zipFile, List<StudentDetails> students) {
        String username = SubmissionFileNames.username(zipFile);

        for (StudentDetails student : students) {
            if (student.getUsername().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
