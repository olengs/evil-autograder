package report;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import grading.QuestionGrade;
import submission.StudentSubmission;
import submission.SubmissionFileNames;
import utils.files.CsvWriter;

public final class ScoreSheetWriter { //writes totals & question marks from each student's highest-scoring submission
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
        rows.add(header.toArray(String[]::new));

        //Record the position of each question column.
        Map<String, Integer> questionColumns = new LinkedHashMap<>();
        int firstQuestionColumn = ScoreSheetColumns.header().length;

        for (int i = 0; i < questionIds.size(); i++) {
            questionColumns.put(
                    questionIds.get(i),
                    firstQuestionColumn + i);
        }

        //Create a row for each selected submission.
        for (var entry : selectSubmissions(results)) {
            StudentSubmission submission = entry.getKey();
            List<QuestionGrade> grades = entry.getValue();
            var student = submission.getStudent();

            String[] row = new String[header.size()];
            Arrays.fill(row, "");

            if (student != null) {
                row[ScoreSheetColumns.username] = student.getUsername();
                row[ScoreSheetColumns.email] = student.getEmail();
            } else {
                //Keep unidentified submissions visible in the output.
                row[ScoreSheetColumns.username] = SubmissionFileNames.username(submission.getOriginalZip());
            }

            row[ScoreSheetColumns.gradeNumerator] = formatMarks(QuestionGrade.totalMarks(grades));

            for (QuestionGrade grade : grades) {
                int column = questionColumns.get(grade.getQuestionId());
                row[column] = formatMarks(grade.getEarnedMarks());
            }

            //OrgDefinedId and grade denominator remain blank:
            //neither value is supplied by the current results model.
            rows.add(row);
        }

        //Create or replace the output CSV using the existing utility.
        CsvWriter.write(output, rows);
    }

    private static List<Entry<StudentSubmission, List<QuestionGrade>>> selectSubmissions(Map<StudentSubmission, List<QuestionGrade>> results) {
        Map<String, Entry<StudentSubmission, List<QuestionGrade>>>
                selected = new LinkedHashMap<>();

        for (var entry : results.entrySet()) {
            StudentSubmission submission = entry.getKey();
            var student = submission.getStudent();

            String key;
            if (student == null) {
                //Unknown students are kept separately by archive path.
                key = "archive:" + submission.getOriginalZip()
                        .toAbsolutePath().normalize();
            } else {
                key = "student:"
                        + ScoreSheetColumns.normalizeUsername(
                                student.getUsername())
                        .toLowerCase(Locale.ROOT);
            }

            var previous = selected.get(key);

            if (previous == null
                    || QuestionGrade.totalMarks(entry.getValue())
                    > QuestionGrade.totalMarks(previous.getValue())) {
                selected.put(key, entry);
            }
        }

        return new ArrayList<>(selected.values());
    }
    
    private static String formatMarks(double marks) {
        return BigDecimal.valueOf(marks).stripTrailingZeros().toPlainString();
    }
}
