package report;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grading.QuestionGrade;
import submission.StudentSubmission;
import utils.files.CsvWriter;

/// writes totals & question marks for every submission archive
public final class ScoreSheetWriter { 
    private ScoreSheetWriter() {}

    public static void write(Map<StudentSubmission, List<QuestionGrade>> results, Path output) throws IOException {

        //Collect question IDs in their original grading order.
        List<String> questionIds = results.values().stream()
                .flatMap(List::stream)
                .map(QuestionGrade::getQuestionId)
                .distinct()
                .toList();

        List<String[]> rows = new ArrayList<>();

        //Build the fixed columns followed by the question columns.
        List<String> header = new ArrayList<>(Arrays.asList(ScoreSheetColumns.header()));
        header.addAll(questionIds);
        int flagColumn = header.size();
        header.add("Identification Flag");
        header.add("Identification Message");
        rows.add(header.toArray(String[]::new));

        //Record the position of each question column.
        Map<String, Integer> questionColumns = new LinkedHashMap<>();
        int firstQuestionColumn = ScoreSheetColumns.header().length;

        for (int i = 0; i < questionIds.size(); i++) {
            questionColumns.put(
                    questionIds.get(i),
                    firstQuestionColumn + i);
        }

        //Keep every archive visible, including repeated submissions by the same student.
        for (var entry : results.entrySet()) {
            StudentSubmission submission = entry.getKey();
            List<QuestionGrade> grades = entry.getValue();
            var student = submission.getStudent();

            String[] row = new String[header.size()];
            Arrays.fill(row, "");

            if (student != null) {
                row[ScoreSheetColumns.username] = student.getUsername();
            }

            row[ScoreSheetColumns.gradeNumerator] = formatMarks(QuestionGrade.totalMarks(grades));
            row[flagColumn] = submission.getIdentificationFlag();
            row[flagColumn + 1] = submission.getIdentificationMessage();

            for (QuestionGrade grade : grades) {
                int column = questionColumns.get(grade.getQuestionId());
                row[column] = formatMarks(grade.getEarnedMarks());
            }

            rows.add(row);
        }

        //Create or replace the output CSV using the existing utility.
        CsvWriter.write(output, rows);
    }

    private static String formatMarks(double marks) {
        return BigDecimal.valueOf(marks).stripTrailingZeros().toPlainString();
    }
}
