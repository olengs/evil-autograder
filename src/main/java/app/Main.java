package app;

import config.AssessmentLoader;
import config.Config;
import grading.AssessmentGrader;
import grading.QuestionGrader;
import grading.TesterLoader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import model.*;
import progress.ProgressListener;
import submission.StudentCsvReader;
import submission.SubmissionLoader;
import utils.FileUtils;
import validation.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.*;

public class Main { //loads project files & validates submissions & grades each question
    public static void main(String[] args) {
        TuiMain.main(args);
    }

    //@1ejun6 > integration flow > load > extract > validate > grade > clean up
    //@1ejun6 > result map keeps each submission together with its question grades
    public static Map<StudentSubmission, List<QuestionGrade>> run(Config config, ProgressListener progress) throws IOException {
        try {
            Map<StudentSubmission, List<QuestionGrade>> results = runAssessment(config, progress);
            progress.batchFinished(); //only finish after grading workspace has been cleaned up
            return results;
        } catch (IOException | RuntimeException | Error failure) {
            progress.failed(failure.getMessage() == null ? failure.toString() : failure.getMessage());
            throw failure;
        }
    }

    private static Map<StudentSubmission, List<QuestionGrade>> runAssessment(Config config, ProgressListener progress) throws IOException {
        //@1ejun6 > 1 > load question requirements & teacher provided tester programs
        progress.preparing("loading assessment & testers");
        var assessment = AssessmentLoader.load(config.getResourcesDirectory());
        var testers = TesterLoader.load(config.getTestersDirectory());
        Path workspace = Files.createTempDirectory("autograder-submissions-");
        Map<StudentSubmission, List<QuestionGrade>> results;
        try {
            //@1ejun6 > extracted submissions stay outside original project files
            //@1ejun6 > 2 > read class list used to identify who submitted each zip
            progress.preparing("reading student scoresheet");
            var students = new StudentCsvReader().read(config.getScoresheetFile());
            if (students.isEmpty()) throw new IOException("no valid students found in scoresheet");

            //@1ejun6 > 3 > wire validation rules together using same file finder
            //@1ejun6 > checks collect issues before answers reach grader
            var finder = new SubmissionFileFinder();
            var validator = new SubmissionValidator(List.of(new QuestionFolderRule(), new RequiredFileRule(finder), new FileNamingRule(finder), new StudentHeaderRule(finder)), List.of(new UnexpectedFileRule()), finder);

            //@1ejun6 > 4 > extract zips > validate each extracted submission
            //@1ejun6 > validation creates ~questionattempt~ objects consumed by grading
            progress.preparing("extracting submission zip files");
            var submissions = SubmissionLoader.loadSubmissions(config.getSubmissionsDirectory(), workspace, students, assessment);
            if (submissions.isEmpty()) throw new IOException("no submission ZIP files found");
            for (var submission : submissions) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("validation interrupted");
                }
                progress.validationStarted(submission.getStudent() == null ? submission.getOriginalZip().getFileName().toString() : submission.getStudent().getUsername());
                validator.validate(submission, assessment);
            }

            //@1ejun6 > 5 > pass prepared submissions to grading > each question gets its answer & requirements & tester & compilation/execution limits.
            results = new AssessmentGrader(new QuestionGrader(config.getCompilationTimeout(), config.getExecutionTimeout())).grade(submissions, assessment, testers, progress);
        } catch (IOException | RuntimeException | Error failure) {
            //@1ejun6 > keep original failure if cleanup also fails
            try {
                FileUtils.deleteDirectory(workspace);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        //@1ejun6 > 6 > clean up extracted files after grading > keep results for printing
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
                if (grade.getStatus() != GradeStatus.completed) System.out.println("    " + grade.getMessage());
            }
            System.out.println("  total > " + entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum());
        }
    }
}
