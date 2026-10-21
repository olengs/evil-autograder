package submission;

import java.nio.file.Path;
import java.util.List;
import model.StudentDetails;

/**
 * Identifies a student using the ZIP filename.
 *
 * @param zipFile the student's ZIP submission
 * @param students list of known students
 * @return the matched Student
 */

public class StudentIdentifier {

    // JC: Might need to refactor for better solve to the problem, maybe compare against inputted class list of student ids
    public StudentDetails identify(Path zipFile, List<StudentDetails> students) {
        String fileName = zipFile.getFileName().toString();
        String username = fileName.substring(0, fileName.length() - 4);
        username = username.replaceFirst("^\\d{4}-\\d{4}-", ""); //provided zips include an academic year prefix

        for (StudentDetails student : students) {
            if (student.getUserName().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
