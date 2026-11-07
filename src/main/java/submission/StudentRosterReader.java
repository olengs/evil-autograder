//reads student identities from a student list scoresheet & derives usernames from email addresses
//logs corrected or invalid rows while continuing through ordinary row errors

package submission;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import utils.logging.JsonLineLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import report.ScoreSheetColumns;

public class StudentRosterReader {
    public List<StudentDetails> read(Path scoresheet, Path logFile) throws IOException {
        List<StudentDetails> roster = new ArrayList<>();

        try (CSVReader csvReader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            boolean hasSkippedHeader = false;
            String[] row;

            while ((row = csvReader.readNext()) != null) {
                if (row.length == 1 && row[0].isBlank()) continue;

                if (!hasSkippedHeader) {
                    hasSkippedHeader = true;
                    continue;
                }

                StudentDetails student = parseStudent(row, csvReader.getRecordsRead(), logFile);
                if (student != null) roster.add(student);
            }
        } catch (CsvValidationException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }

        return roster;
    }

    private StudentDetails parseStudent(String[] row, long recordNumber, Path logFile) throws IOException {
        if (row.length <= ScoreSheetColumns.email) {
            JsonLineLogger.append(logFile, Map.of("status", "skipped", "row", recordNumber, "issue", "invalid record", "details", "fewer than 5 columns"));
            return null;
        }

        String name = row[ScoreSheetColumns.firstName].strip();
        String studentId = row[ScoreSheetColumns.orgDefinedId].strip();
        String listedUsername = row[ScoreSheetColumns.username].strip().replaceAll("[^\\p{Alnum}.]", "").replaceAll("^\\.+|\\.+$", "");
        String email = row[ScoreSheetColumns.email].strip();
        int atIndex = email.indexOf('@');

        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
            JsonLineLogger.append(logFile, Map.of("status", "skipped", "row", recordNumber,
                    "issue", "invalid email; > student skipped", "studentid", studentId,
                    "name", name, "email", email));
            return null;
        }

        //email is identity source when listed username contains decorations or disagrees
        String username = email.substring(0, atIndex);
        if (!listedUsername.equals(username)) {
            JsonLineLogger.append(logFile, Map.of("status", "fixed", "row", recordNumber, "issue", "username mismatch > email username used", "studentid", studentId, "name", name, "listedusername", listedUsername, "emailusername", username));
        }
        return new StudentDetails(name, username, email);
    }
}
