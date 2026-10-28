package grading;

import app.Pair1;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import model.*;
import utils.FileUtils;

public class AssessmentGrader {
    private static final Map<String, Integer> MAX_MARKS = Map.of("q1a", 3, "q1b", 3, "q2a", 5, "q2b", 5, "q3", 4);

    public static void run(Path project) throws IOException {
        project = project.toAbsolutePath().normalize();
        if (project.getFileName().toString().equals("student-submission")) project = project.getParent();
        Path config = Files.isDirectory(project.resolve("Project")) ? project.resolve("Project") : project;
        var testers = TesterLoader.LoadTesterFiles(config.resolve("Tester-Files").toString());
        Path workspace = Files.createTempDirectory("assessment-");
        try {
            var results = grade(Pair1.run(project, workspace), testers);
            results.forEach((submission, grades) -> {
                var student = submission.getStudent();
                System.out.println("\n" + (student == null ? submission.getOriginalZip().getFileName()
                        : student.getName() + " (" + student.getUserName() + ")"));
                for (QuestionGrade grade : grades) {
                    System.out.printf("  %s: %.0f/%d%s%n", grade.getQuestionId(), grade.getEarnedMarks(),
                            MAX_MARKS.get(grade.getQuestionId().toLowerCase(Locale.ROOT)),
                            grade.getStatus() == GradeStatus.COMPLETED ? "" : " [" + grade.getStatus() + "]");
                    if (grade.getStatus() != GradeStatus.COMPLETED) {
                        String diagnostic = grade.getProcessResult().map(result -> result.getErrorOutput().lines()
                                .filter(line -> !line.isBlank()).findFirst().orElse("")).orElse("");
                        System.out.println("    " + grade.getMessage() + (diagnostic.isEmpty() ? "" : ": " + diagnostic));
                    }
                }
                System.out.printf("  Total: %.0f/20%n", grades.stream().mapToDouble(QuestionGrade::getEarnedMarks).sum());
            });
        } finally {
            FileUtils.DeleteFolder(workspace);
        }
    }

    public static Map<StudentSubmission, List<QuestionGrade>> grade(
            List<StudentSubmission> submissions, Map<String, TesterDefinition> testers) {
        Map<StudentSubmission, List<QuestionGrade>> results = new LinkedHashMap<>();
        for (StudentSubmission submission : submissions) {
            List<QuestionGrade> grades = new ArrayList<>();
            for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
                String id = attempt.getQuestionDefinition().getId();
                if (!attempt.isValid()) {
                    String reason = submission.getSubmissionIssues().stream()
                            .filter(issue -> issue.getSeverity() == IssueSeverity.ERROR)
                            .filter(issue -> issue.getQuestionId() == null || issue.getQuestionId().equals(id))
                            .map(SubmissionIssue::getMessage).distinct().collect(Collectors.joining("; "));
                    grades.add(new QuestionGrade(id, GradeStatus.SKIPPED, 0, null, null, reason));
                    continue;
                }
                TesterDefinition tester = testers.get(id.toLowerCase(Locale.ROOT));
                if (tester == null) {
                    grades.add(new QuestionGrade(id, GradeStatus.SKIPPED, 0, null, null, "Missing tester"));
                    continue;
                }
                try (var grader = new QuestionGrader(new QuestionTestPack(attempt.getQuestionDefinition(), attempt, tester))) {
                    grades.add(grader.gradeQuestion());
                } catch (Exception error) {
                    grades.add(new QuestionGrade(id, GradeStatus.RUNTIME_ERROR, 0, null, null, error.toString()));
                }
            }
            results.put(submission, grades);
        }
        return results;
    }

}
