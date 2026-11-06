// Reads roster records and derives each student's username from their email.
// Logs corrected or invalid CSV rows while continuing through ordinary row errors.

package submission;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import model.StudentDetails;
import report.ScoreSheetColumns;
import utils.Logger;

public class StudentCsvReader {
    //@1ejun6 > read one row at a time > skip header & blank rows
    public List<StudentDetails> read(Path scoresheet, Path logFile) throws IOException {
        List<StudentDetails> students = new ArrayList<>(); //@1ejun6 > grow in size

        //@1ejun6 > open file as utf-8 > csvreader split it into csv rows
        try (CSVReader csvReader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            boolean hasSkippedHeader = false;
            String[] row;

            while ((row = csvReader.readNext()) != null) { //@1ejun6 > read one string[] row at a time > null means end of file
                if (row.length == 1 && row[0].isBlank()) { //@1ejun6 > skip empty & whitespace only rows
                    continue;
                }

                //@1ejun6 > first nonblank row is scoresheet header
                if (!hasSkippedHeader) {
                    hasSkippedHeader = true;
                    continue;
                }

                StudentDetails student = parseStudent(row, csvReader.getRecordsRead(), logFile);
                if (student != null) students.add(student);
            }
        } catch (CsvValidationException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }

        return students;
    }

    //@1ejun6 > validate row & log issues > return null if skipped
    private StudentDetails parseStudent(String[] row, long recordNumber, Path logFile) throws IOException {
        if (row.length <= ScoreSheetColumns.email) {
            Logger.logError(logFile, Map.of("status", "skipped", "row", recordNumber,
                    "issue", "invalid record", "details", "fewer than 5 columns"));
            return null;
        }

        String name = row[ScoreSheetColumns.firstName].strip();
        String studentId = row[ScoreSheetColumns.orgDefinedId].strip();
        //@1ejun6 > remove username decorations & dots at start or end
        String listedUsername = row[ScoreSheetColumns.username].strip().replaceAll("[^\\p{Alnum}.]", "").replaceAll("^\\.+|\\.+$", "");
        String email = row[ScoreSheetColumns.email].strip();
        int atIndex = email.indexOf('@');

        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
            Logger.logError(logFile, Map.of("status", "skipped", "row", recordNumber,
                    "issue", "invalid email; > student skipped", "studentid", studentId,
                    "name", name, "email", email));
            return null;
        }

        //@1ejun6 > use everything before @ as username
        String username = email.substring(0, atIndex);
        if (!listedUsername.equals(username)) {
            Logger.logError(logFile, Map.of("status", "fixed", "row", recordNumber,
                    "issue", "username mismatch > email username used", "studentid", studentId,
                    "name", name, "listedusername", listedUsername, "emailusername", username));
        }
        return new StudentDetails(name, username, email);
    }
}
