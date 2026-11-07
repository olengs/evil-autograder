//matches a submission zip filename to a student list username without regard to capitalization
//uses shared filename convention to remove zip suffixes & optional academic-year prefixes

package submission;

import java.nio.file.Path;
import java.util.List;

public class StudentIdentifier {
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
