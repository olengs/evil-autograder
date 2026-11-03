// Matches a ZIP submission to a roster student.
// Uses the archive username and optional academic-year prefix.

package autograder.submission;

import autograder.model.Student;
import java.nio.file.Path;
import java.util.List;

public class StudentIdentifier {

    // JC: Might need to refactor for better solve to the problem, maybe compare against inputted class list of student ids
    public Student identify(Path zipFile, List<Student> students) {
        String username = SubmissionFileNames.baseNameWithoutZipExtension(zipFile);
        username = username.replaceFirst("^\\d{4}-\\d{4}-", ""); //provided zips include an academic year prefix

        for (Student student : students) {
            if (student.getUserName().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
