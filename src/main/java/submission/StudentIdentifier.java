// Identifies a submission's student using its ZIP filename and the class roster.
// Removes the optional academic-year prefix before matching the username.

package submission;

import java.nio.file.Path;
import java.util.List;
import model.StudentDetails;

public class StudentIdentifier {
    public StudentDetails identify(Path zipFile, List<StudentDetails> students) {
        String username = SubmissionFileNames.baseNameWithoutZipExtension(zipFile);
        username = username.replaceFirst("^\\d{4}-\\d{4}-", ""); //@1ejun6 > provided zips include an academic year prefix

        for (StudentDetails student : students) {
            if (student.getUsername().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
