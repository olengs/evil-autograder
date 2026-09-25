package autograder.submission;

import autograder.model.Student;
import java.nio.file.Path;
import java.util.List;

public class StudentIdentifier {

    /**
     * Identifies a student using the ZIP filename.
     *
     * @param zipFile the student's ZIP submission
     * @param students list of known students
     * @return the matched Student
     */
    public Student identify(
            Path zipFile,
            List<Student> students) {

        String fileName =
                zipFile.getFileName().toString();

        String username =
                fileName.substring(
                        0,
                        fileName.length() - 4
                );

        for (Student student : students) {

            if (student.getUserName()
                    .equalsIgnoreCase(username)) {

                return student;
            }
        }

        return null;
    }
}