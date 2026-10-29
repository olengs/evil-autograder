package app;

import config.AssessmentLoader;
import config.Config;
import grading.AssessmentGrader;
import grading.QuestionGrader;
import grading.TesterLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import model.*;
import submission.StudentCsvReader;
import submission.SubmissionLoader;
import utils.FileUtils;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.*;

public class Main { //loads project files & validates submissions & grades each question
    public static void main(String[] args) {
        try {
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            printResults(run(config));
        } catch (IOException | RuntimeException error) {
            System.err.println("error > " + error.getMessage());
            System.exit(1);
        }
    }

    public static Map<StudentSubmission, List<QuestionGrade>> run(Config config) throws IOException {
        var assessment = AssessmentLoader.load(config.getResourcesDirectory());
        var testers = TesterLoader.load(config.getTestersDirectory());
        Path workspace = Files.createTempDirectory("autograder-submissions-");
        Map<StudentSubmission, List<QuestionGrade>> results;
        try {
            //@1ejun6 > logs & extracted submissions stay outside original project files
            var students = new StudentCsvReader().read(config.getScoresheetFile(), workspace.resolve("csv.log.jsonl"));
            if (students.isEmpty()) throw new IOException("no valid students found in scoresheet");
            var finder = new SubmissionFileFinder();
            var validator = new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(finder),
                    new FileNamingRule(finder), new StudentHeaderRule(finder)), List.of(new UnexpectedFileRule()), finder);
            var submissions = SubmissionLoader.loadSubmissions(config.getSubmissionsDirectory(), workspace, students, assessment);
            if (submissions.isEmpty()) throw new IOException("no submission ZIP files found");
            for (var submission : submissions) validator.validate(submission, assessment);
            results = new AssessmentGrader(new QuestionGrader(config.getCompilationTimeout(), config.getExecutionTimeout()))
                    .grade(submissions, assessment, testers);
        } catch (IOException | RuntimeException | Error failure) {
            //@1ejun6 > keep original failure if cleanup also fails
            try {
                FileUtils.deleteDirectory(workspace);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        FileUtils.deleteDirectory(workspace);
        return results;
    }

    public static void printResults(Map<StudentSubmission, List<QuestionGrade>> results) {
        for (var entry : results.entrySet()) {
            var student = entry.getKey().getStudent();
            System.out.println("\n" + (student == null ? entry.getKey().getOriginalZip().getFileName() : student.getUsername()));
            for (var issue : entry.getKey().getSubmissionIssues()) System.out.println("  " + issue);
            for (var grade : entry.getValue()) {
                System.out.println("  " + grade.getQuestionId() + ": " + grade.getEarnedMarks() + " [" + grade.getStatus() + "]");
                if (grade.getStatus() != GradeStatus.COMPLETED) System.out.println("    " + grade.getMessage());
            }
            System.out.println("  total > " + entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum());
        }
    }
}
