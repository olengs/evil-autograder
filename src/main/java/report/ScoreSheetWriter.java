package report;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import grading.QuestionGrade;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import submission.StudentDetails;
import submission.StudentSubmission;
import utils.files.CsvWriter;

public final class ScoreSheetWriter { //writes totals & question marks from each student's highest-scoring submission
    private ScoreSheetWriter() {}

    public static void write(Path scoresheet, Map<StudentSubmission, List<QuestionGrade>> results, Path output) throws IOException {
        if (scoresheet.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || (Files.exists(output) && Files.isSameFile(scoresheet, output))) {
            throw new IOException("output scoresheet must differ from input scoresheet > " + output);
        }
        List<String> questions = results.values().stream().flatMap(List::stream)
                .map(QuestionGrade::getQuestionId).distinct().toList();
        List<List<String>> rows = readRows(scoresheet, questions);
        if (!rows.isEmpty()) {
            Map<String, Integer> columns = questionColumns(rows.getFirst(), questions);
            fillGrades(rows, highestStudentGrades(results), columns);
        }
        CsvWriter.write(output, rows.stream().map(row -> row.toArray(String[]::new)).toList());
    }

    private static Map<String, List<QuestionGrade>> highestStudentGrades(Map<StudentSubmission, List<QuestionGrade>> results) {
        Map<String, List<QuestionGrade>> selected = new HashMap<>();
        for (var entry : results.entrySet()) {
            StudentDetails student = entry.getKey().getStudent();
            if (student == null) continue;

            //keep one whole submission > question scores must add up to chosen final total
            selected.merge(student.getUsername(), entry.getValue(),
                    (previous, current) -> QuestionGrade.totalMarks(current) > QuestionGrade.totalMarks(previous) ? current : previous);
        }
        return selected;
    }

    private static Map<String, Integer> questionColumns(List<String> header, List<String> questions) {
        Map<String, Integer> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        //index existing columns once > reuse question headers regardless of casing
        for (int index = ScoreSheetColumns.gradeDenominator + 1; index < header.size(); index++) {
            columns.putIfAbsent(header.get(index).strip(), index);
        }
        for (String question : questions) {
            if (!columns.containsKey(question)) {
                columns.put(question, header.size());
                header.add(question);
            }
        }
        return columns;
    }

    private static List<List<String>> readRows(Path scoresheet, List<String> questions) throws IOException {
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(scoresheet))) {
            List<List<String>> rows = new ArrayList<>();
            for (String[] row : reader.readAll()) {
                if (ScoreSheetColumns.isBlankRow(row)) continue;
                if (rows.isEmpty()) ScoreSheetColumns.validateHeader(row);
                rows.add(new ArrayList<>(Arrays.asList(row)));
            }
            if (rows.isEmpty()) return rows;
            List<String> header = rows.getFirst();
            while (header.size() <= ScoreSheetColumns.gradeDenominator) header.add(ScoreSheetColumns.header()[header.size()]);
            //omit legacy markers & questions from earlier assessments > keep unrelated columns
            for (int column = header.size() - 1; column > ScoreSheetColumns.gradeDenominator; column--) {
                String label = header.get(column).strip();
                if (!label.equalsIgnoreCase("End-of-Line Indicator")
                        && (!label.matches("(?i)Q\\d+[a-z]*") || questions.stream().anyMatch(label::equalsIgnoreCase))) continue;
                for (List<String> row : rows) if (column < row.size()) row.remove(column);
            }
            return rows;
        } catch (CsvException error) {
            throw new IOException("cannot read scoresheet > " + scoresheet, error);
        }
    }

    private static void fillGrades(List<List<String>> rows, Map<String, List<QuestionGrade>> selected, Map<String, Integer> questionColumns) {
        for (List<String> row : rows.subList(1, rows.size())) { //preserve header row
            if (row.size() <= ScoreSheetColumns.email) continue;
            while (row.size() < rows.getFirst().size()) row.add("");
            row.set(ScoreSheetColumns.orgDefinedId, row.get(ScoreSheetColumns.orgDefinedId).replaceFirst("^#+", ""));
            row.set(ScoreSheetColumns.username, row.get(ScoreSheetColumns.username).replaceFirst("^#+", ""));
            if (row.get(ScoreSheetColumns.lastName).equals("_")) row.set(ScoreSheetColumns.lastName, "");
            if (row.get(ScoreSheetColumns.firstName).equals("_")) row.set(ScoreSheetColumns.firstName, "");

            var username = StudentDetails.usernameFromEmail(row.get(ScoreSheetColumns.email));
            if (username.isEmpty()) continue;

            List<QuestionGrade> grades = selected.get(username.orElseThrow());
            if (grades == null) continue;

            row.set(ScoreSheetColumns.gradeNumerator, formatMarks(QuestionGrade.totalMarks(grades)));
            for (QuestionGrade grade : grades) {
                row.set(questionColumns.get(grade.getQuestionId()), formatMarks(grade.getEarnedMarks()));
            }
        }
    }

    private static String formatMarks(double marks) {
        return BigDecimal.valueOf(marks).stripTrailingZeros().toPlainString();
    }
}
