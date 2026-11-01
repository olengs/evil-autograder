package submission;

import java.nio.file.Path;
import java.util.List;
import model.StudentDetails;

/**
 * identifies a student using zip filename
 *
 * @param zipFile > students zip submission
 * @param students > list of known students
 * @return > matched submissionstudent
 */

public class StudentIdentifier { //matches a submitted zip filename to a student in scoresheet
    //@1ejun6 > jc > might need to refactor for better solve to the problem > maybe compare against inputted class list of student ids
    //@1ejun6 > addressed above > compare zip username against students loaded from scoresheet
    //@1ejun6 > remove academic year prefix before matching > return null when no student matches
    public StudentDetails identify(Path zipFile, List<StudentDetails> students) {
        String fileName = zipFile.getFileName().toString();
        String username = fileName.substring(0, fileName.length() - 4);
        username = username.replaceFirst("^\\d{4}-\\d{4}-", ""); //@1ejun6 > provided zips include an academic year prefix

        for (StudentDetails student : students) {
            if (student.getUsername().equalsIgnoreCase(username)) {
                return student;
            }
        }
        return null;
    }
}
