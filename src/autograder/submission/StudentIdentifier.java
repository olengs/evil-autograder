package autograder.submission;

import autograder.model.Student;
import java.nio.file.Path;
import java.util.List;

/**
 * Identifies a student using the ZIP filename.
 *
 * @param zipFile the student's ZIP submission
 * @param students list of known students
 * @return the matched Student
 */

public class StudentIdentifier {

    // JC: Might need to refactor for better solve to the problem, maybe compare against inputted class list of student ids
    public Student identify(Path zipFile, List<Student> students) {
        String fileName = zipFile.getFileName().toString();
        String username = fileName.substring(0, fileName.length() - 4);
        username = username.replaceFirst("^\\d{4}-\\d{4}-", ""); //provided zips include an academic year prefix

        Student match = null;
        for (Student student : students) {
            if (student.getUserName().equalsIgnoreCase(username)) {
                if (match != null) {
                    throw new IllegalArgumentException("ambiguous roster identity > " + username);
                }
                match = student;
            }
        }
        return match;
    }
}
