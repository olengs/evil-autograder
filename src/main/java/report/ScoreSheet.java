package report;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import model.QuestionGrade;
import model.StudentSubmission;
import submission.SubmissionFileNames;
import submission.SubmissionLoader;
import utils.CsvWriter;
import utils.StudentHeaderUtils;

public class ScoreSheet { //creates the scoresheet from the zips when it is not provided & fills in each student's total
    private static final String[] columns = {"OrgDefinedId", "Username", "Last Name", "First Name", "Email", "Calculated Final Grade Numerator", "Calculated Final Grade Denominator", "End-of-Line Indicator"};
    private static final int emailColumn = 4; //same column positions StudentCsvReader reads
    private static final int gradeColumn = 5;
    private static final String emailDomain = "computing.smu.edu.sg"; //domain of every email in the provided scoresheet

    //@1ejun6 > no scoresheet provided > create one row per student
    public static boolean createIfMissing(Path scoresheet, Path submissionsFolder) throws IOException {
        if (Files.exists(scoresheet)) {
            return false;
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(columns);
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Path zip : SubmissionLoader.findZipFiles(submissionsFolder)) {
            String username = SubmissionFileNames.username(zip);
            if (usernames.add(username)) rows.add(studentRow(zip, username));
        }

        CsvWriter.write(scoresheet, rows);
        return true;
    }

    //@1ejun6 > copy scoresheet > fill in each students total
    public static void write(Path scoresheet, Map<StudentSubmission, List<QuestionGrade>> results, Path output) throws IOException {
        List<String[]> rows = readRows(scoresheet);
        Map<String, Double> totals = studentTotals(results);
        fillGrades(rows, totals);
        CsvWriter.write(output, rows);
    }

    //@1ejun6 > zip name & student header > create one row
    private static String[] studentRow(Path zip, String username) {
        var header = StudentHeaderUtils.readFields(javaSources(zip));
        String email = header.email();
        if (!email.toLowerCase(Locale.ROOT).startsWith(username.toLowerCase(Locale.ROOT) + "@")) {
            email = username + "@" + emailDomain;
        }
        return new String[] {"", username, "", header.name(), email, "", "", ""};
    }

    //@1ejun6 > sum question marks > keep each students highest total
    private static Map<String, Double> studentTotals(Map<StudentSubmission, List<QuestionGrade>> results) {
        Map<String, Double> totals = new HashMap<>();
        for (var entry : results.entrySet()) {
            var student = entry.getKey().getStudent();
            if (student == null) continue;

            double total = 0;
            for (QuestionGrade question : entry.getValue()) {
                total += question.getEarnedMarks();
            }
            totals.merge(student.getUsername(), total, Math::max);
        }
        return totals;
    }

    //@1ejun6 > read all csv rows > include header
    private static List<String[]> readRows(Path scoresheet) throws IOException {
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            return reader.readAll();
        } catch (CsvException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }
    }

    //@1ejun6 > clean roster markers > match email username > update grade column
    private static void fillGrades(List<String[]> rows, Map<String, Double> totals) {
        for (int index = 1; index < rows.size(); index++) { //keep the header unchanged
            String[] row = rows.get(index);
            if (row.length <= gradeColumn) continue;

            row[0] = row[0].replaceFirst("^#+", "");
            row[1] = row[1].replaceFirst("^#+", "");
            if (row[2].equals("_")) row[2] = "";
            if (row[3].equals("_")) row[3] = "";
            if (row.length > 7 && row[7].equals("#")) row[7] = "";

            String email = row[emailColumn].strip();
            int atIndex = email.indexOf('@');
            if (atIndex <= 0) continue;

            String username = email.substring(0, atIndex);
            Double total = totals.get(username);
            if (total == null) continue;

            row[gradeColumn] = BigDecimal.valueOf(total).stripTrailingZeros().toPlainString();
        }
    }

    //@1ejun6 > read java files inside zip > find student header
    private static String javaSources(Path zip) {
        StringBuilder sources = new StringBuilder();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : Collections.list(file.entries())) {
                if (entry.getName().endsWith(".java")) {
                    try (var source = file.getInputStream(entry)) {
                        sources.append(new String(source.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            //a broken zip still gets a row > the loader reports the zip itself
        }
        return sources.toString();
    }
}
