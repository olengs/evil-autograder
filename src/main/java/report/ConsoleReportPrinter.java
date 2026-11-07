//prints each submission's identity & validation findings & question grades & total marks
//presents results from normal grading or standalone verification without changing recorded grades

package report;

import grading.GradeStatus;
import grading.QuestionGrade;
import java.util.List;
import java.util.Map;
import submission.StudentDetails;
import submission.StudentSubmission;
import validation.SubmissionIssue;

public final class ConsoleReportPrinter {
    private ConsoleReportPrinter() {}

    public static void print(Map<StudentSubmission, List<QuestionGrade>> results) {
        for (var entry : results.entrySet()) {
            StudentSubmission submission = entry.getKey();
            StudentDetails student = submission.getStudent();
            System.out.println("\n" + (student == null ? submission.getOriginalZip().getFileName() : student.getUsername()));

            for (SubmissionIssue issue : submission.getSubmissionIssues()) System.out.println("  " + issue);

            for (QuestionGrade grade : entry.getValue()) {
                System.out.println("  " + grade.getQuestionId() + " > " + grade.getEarnedMarks() + " [" + grade.getStatus() + "]");
                if (grade.getStatus() != GradeStatus.completed) System.out.println("    " + grade.getMessage());
            }

            double totalMarks = entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum();
            System.out.println("  total > " + totalMarks);
        }
    }
}
