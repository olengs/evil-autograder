package report;

import java.util.List;
import java.util.Map;

import grading.GradeStatus;
import grading.QuestionGrade;
import submission.StudentSubmission;
import validation.SubmissionIssue;

/// prints student identities, validation findings & question marks
public final class ConsoleReportPrinter { 
    private ConsoleReportPrinter() {}

    public static void print(Map<StudentSubmission, List<QuestionGrade>> results) {
        for (var entry : results.entrySet()) {
            StudentSubmission submission = entry.getKey();
            System.out.println("\n" + submission.getDisplayName());

            for (SubmissionIssue issue : submission.getSubmissionIssues()) System.out.println("  " + issue);

            for (QuestionGrade grade : entry.getValue()) {
                System.out.println("  " + grade.getQuestionId() + " > " + grade.getEarnedMarks() + " [" + grade.getStatus() + "]");
                if (grade.getStatus() != GradeStatus.completed) System.out.println("    " + grade.getMessage());
            }

            double totalMarks = QuestionGrade.totalMarks(entry.getValue());
            System.out.println("  total > " + totalMarks);
        }
    }
}
