package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import report.ScoreSheetColumns;
import utils.logging.JsonLineLogger;

public class StudentRosterReader { //reads student identities from CSV & logs invalid or corrected rows
    public List<StudentDetails> read(Path scoresheet) throws IOException {
        List<StudentDetails> roster = new ArrayList<>();

        try (CSVReader csvReader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            String[] header = null;
            String[] row;

            while ((row = csvReader.readNext()) != null) {
                if (ScoreSheetColumns.isBlankRow(row)) continue;

                if (header == null) {
                    ScoreSheetColumns.validateHeader(row);
                    header = row;
                    continue;
                }

                StudentDetails student = parseStudent(row, header, csvReader.getRecordsRead());
                if (student != null) roster.add(student);
            }
        } catch (CsvValidationException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }

        return roster;
    }

    private StudentDetails parseStudent(String[] row, String[] header, long recordNumber) throws IOException {
        if (row.length <= ScoreSheetColumns.username) {
            JsonLineLogger.getInstance().append(Map.of("status", "skipped", "row", recordNumber, "issue", "invalid record", "details", "fewer than 2 columns"));
            return null;
        }

        String username = ScoreSheetColumns.normalizeUsername(row[ScoreSheetColumns.username]);

        if (username.isBlank()) {
            JsonLineLogger.getInstance().append(Map.of("status", "skipped", "row", recordNumber,
                    "issue", "missing username > student skipped"));
            return null;
        }

        return new StudentDetails(optionalField(header, row, "First Name"), username,
                optionalField(header, row, "Email"));
    }

    private String optionalField(String[] header, String[] row, String label) {
        for (int index = 0; index < Math.min(header.length, row.length); index++) {
            if (header[index].strip().equalsIgnoreCase(label)) return row[index].strip();
        }
        return "";
    }
}
