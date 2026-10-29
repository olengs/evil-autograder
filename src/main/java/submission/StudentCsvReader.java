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
import utils.Logger;

public class StudentCsvReader { //read scoresheet & corrects usernames from email & records row issues without stopping for ordinary invalid records
    public List<StudentDetails> read(Path scoresheet) throws IOException { //@1ejun6 > checked
        return read(scoresheet, scoresheet.toAbsolutePath().getParent().resolve("csv.log.jsonl"));
    }

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

                long recordNumber = csvReader.getRecordsRead();
                //@1ejun6 > columns > id & username & last name & first name & email
                if (row.length < 5) {
                    Logger.logError(logFile, Map.of("status", "skipped", "row", recordNumber, "issue", "invalid record", "details", "fewer than 5 columns"));
                    continue;
                }
                String name = row[3].strip();
                String studentId = row[0].strip();
                String listedUsername = row[1].strip().replaceAll("[^\\p{Alnum}.]", "").replaceAll("^\\.+|\\.+$", "");
                //@1ejun6 > [^\\p{Alnum}.] > removes anything except letters & digits & dots anywhere
                //@1ejun6 > ^\\.+|\\.+$ > removes one or more dots at the start or end
                String email = row[4].strip();
                int atIndex = email.indexOf('@');

                if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
                    Logger.logError(logFile, Map.of("status", "skipped", "row", recordNumber, "issue", "invalid email; > student skipped", "studentid", studentId, "name", name, "email", email));
                    continue;
                }

                //@1ejun6 > username is everything before @ in email > eg. ping.lee.2023
                String username = email.substring(0, atIndex);
                if (!listedUsername.equals(username)) {
                    Logger.logError(logFile, Map.of("status", "fixed", "row", recordNumber, "issue", "username mismatch > email username used", "studentid", studentId, "name", name, "listedusername", listedUsername, "emailusername", username));
                }

                students.add(new StudentDetails(name, username, email));
            }
        } catch (CsvValidationException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }

        return students;
    }
}
