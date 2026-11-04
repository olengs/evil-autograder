package report;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import model.QuestionGrade;
import model.StudentSubmission;

public class ScoreSheet { //creates the scoresheet from the zips when it is not provided & fills in each student's total


    // To do: May need to adjust on how the cvs fields now its matching with June's cvs reader. If we not will cause error.
    private static final String[] COLUMNS = {"OrgDefinedId", "Username", "Last Name", "First Name", "Email",
            "Calculated Final Grade Numerator", "Calculated Final Grade Denominator", "End-of-Line Indicator"};
    private static final int EMAIL = 4; //same column positions StudentCsvReader reads
    private static final int GRADE = 5;
    private static final String EMAIL_DOMAIN = "computing.smu.edu.sg"; //domain of every email in the provided scoresheet
    private static final Pattern NAME_FIELD = fieldPattern("Name");
    private static final Pattern EMAIL_FIELD = fieldPattern("Email(?:\\h+ID)?");

    /**
     * username comes from the zip name > name & email from the first java header that has them
     * the whole name goes in First Name & Last Name is _ like the provided file > headers do not say which part is the surname
     *
     * @return > true when a scoresheet was created \ false when one is provided
     */
    public static boolean createIfMissing(Path scoresheet, Path submissionsFolder) throws IOException {
        if (Files.exists(scoresheet)) {
            return false;
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(COLUMNS);
        try (Stream<Path> files = Files.list(submissionsFolder)) {
            for (Path zip : files.filter(path -> path.toString().toLowerCase().endsWith(".zip")).sorted().toList()) {
                String fileName = zip.getFileName().toString();
                String username = fileName.substring(0, fileName.length() - 4).replaceFirst("^\\d{4}-\\d{4}-", ""); //same rule as StudentIdentifier
                if (rows.stream().anyMatch(row -> row[1].equalsIgnoreCase("#" + username))) continue; //one row per student

                String sources = javaSources(zip);
                String email = fieldValue(sources, EMAIL_FIELD);
                if (!email.toLowerCase().startsWith(username.toLowerCase() + "@")) email = username + "@" + EMAIL_DOMAIN;
                rows.add(new String[] {"", "#" + username, "_", fieldValue(sources, NAME_FIELD), email, "", "", "#"});
            }
        }
        save(scoresheet, rows);
        return true;
    }

    /**
     * copies the scoresheet & fills in the grade column > every other column & row stays as it is
     * a row is matched by the username in its email > the same rule StudentCsvReader uses
     * a student without a graded submission is left blank
     */
    public static void write(Path scoresheet, Map<StudentSubmission, List<QuestionGrade>> results, Path output) throws IOException {
        Map<String, Double> totals = new HashMap<>();
        for (var entry : results.entrySet()) {
            if (entry.getKey().getStudent() != null) {
                double total = entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum();
                totals.merge(entry.getKey().getStudent().getUsername(), total, Math::max); //two zips for one student > the better one counts
            }
        }

        List<String[]> rows;
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            rows = reader.readAll();
        } catch (CsvException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }
        for (String[] row : rows) { //the header row has no @ so it is never matched
            if (row.length <= GRADE) continue;
            String email = row[EMAIL].strip();
            Double total = email.indexOf('@') > 0 ? totals.get(email.substring(0, email.indexOf('@'))) : null;
            if (total != null) row[GRADE] = BigDecimal.valueOf(total).stripTrailingZeros().toPlainString();
        }
        save(output, rows);
    }

    private static void save(Path file, List<String[]> rows) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(file), CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END)) {
            writer.writeAll(rows, false); //quotes only values that need them > keeps the provided format
        }
    }

    private static String javaSources(Path zip) {
        StringBuilder sources = new StringBuilder();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : Collections.list(file.entries())) {
                if (entry.getName().endsWith(".java")) {
                    sources.append(new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                }
            }
        } catch (IOException | RuntimeException error) {
            //a broken zip still gets a row > the loader reports the zip itself
        }
        return sources.toString();
    }

    private static Pattern fieldPattern(String label) {
        return Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*(.*?)\\h*(?:\\*/)?\\h*$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    private static String fieldValue(String sources, Pattern field) { //first value that is not blank
        Matcher matcher = field.matcher(sources);
        while (matcher.find()) {
            if (!matcher.group(1).isBlank()) return matcher.group(1).strip();
        }
        return "";
    }
}
