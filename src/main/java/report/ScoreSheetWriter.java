//writes student grade totals into a copy of student list scoresheet
//preserves unmatched grades & uses highest submission total when a username has multiple archives

package report;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import grading.QuestionGrade;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import submission.StudentDetails;
import submission.StudentSubmission;

public final class ScoreSheetWriter {
    private ScoreSheetWriter() {}

    public static void write(Path scoresheet, Map<StudentSubmission, List<QuestionGrade>> results, Path output) throws IOException {
        if (scoresheet.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || (Files.exists(output) && Files.isSameFile(scoresheet, output))) {
            throw new IOException("output scoresheet must differ from input scoresheet > " + output);
        }
        List<String[]> rows = readRows(scoresheet);
        Map<String, Double> totals = highestStudentTotals(results);
        fillGrades(rows, totals);
        CsvWriter.write(output, rows);
    }

    private static Map<String, Double> highestStudentTotals(Map<StudentSubmission, List<QuestionGrade>> results) {
        Map<String, Double> totals = new HashMap<>();
        for (var entry : results.entrySet()) {
            StudentDetails student = entry.getKey().getStudent();
            if (student == null) continue;

            double total = 0;
            for (QuestionGrade grade : entry.getValue()) total += grade.getEarnedMarks();
            //multiple archives for one username contribute highest total > do not add submission totals together
            totals.merge(student.getUsername(), total, Math::max);
        }
        return totals;
    }

    private static List<String[]> readRows(Path scoresheet) throws IOException {
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            return reader.readAll();
        } catch (CsvException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }
    }

    private static void fillGrades(List<String[]> rows, Map<String, Double> totals) {
        for (int index = 1; index < rows.size(); index++) { //preserve header row
            String[] row = rows.get(index);
            if (row.length <= ScoreSheetColumns.gradeNumerator) continue;

            row[ScoreSheetColumns.orgDefinedId] = row[ScoreSheetColumns.orgDefinedId].replaceFirst("^#+", "");
            row[ScoreSheetColumns.username] = row[ScoreSheetColumns.username].replaceFirst("^#+", "");
            if (row[ScoreSheetColumns.lastName].equals("_")) row[ScoreSheetColumns.lastName] = "";
            if (row[ScoreSheetColumns.firstName].equals("_")) row[ScoreSheetColumns.firstName] = "";
            if (row.length > ScoreSheetColumns.endOfLine && row[ScoreSheetColumns.endOfLine].equals("#")) row[ScoreSheetColumns.endOfLine] = "";

            String email = row[ScoreSheetColumns.email].strip();
            int atIndex = email.indexOf('@');
            if (atIndex <= 0) continue;

            String username = email.substring(0, atIndex);
            Double total = totals.get(username);
            if (total == null) continue;

            row[ScoreSheetColumns.gradeNumerator] = BigDecimal.valueOf(total).stripTrailingZeros().toPlainString();
        }
    }
}
