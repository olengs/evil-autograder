// Connects roster loading, submission validation, and grading.
// Configures the pipeline and prints its collected results.

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
import junit.JUnitRunner;
import model.*;
import report.ScoreSheet;
import submission.StudentCsvReader;
import submission.SubmissionLoader;
import utils.FileUtils;
import utils.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.*;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("usage > [config.properties]");
            if (args.length == 1 && args[0].startsWith("--")) {
                throw new IllegalArgumentException("unknown option > " + args[0]);
            }
            Config config = args.length == 0 ? new Config() : new Config(Path.of(args[0]));
            var results = run(config);
            printResults(results);
            JUnitRunner.verify(config, results);

            //7 > fill the provided scoresheet with each student's total
            Path scoresheet = config.getOutputDirectory().resolve(config.getScoresheetFile().getFileName());
            ScoreSheet.write(config.getScoresheetFile(), results, scoresheet);
            System.out.println("\nscoresheet saved > " + scoresheet);
        } catch (IOException | RuntimeException error) {
            System.err.println("error > " + error.getMessage());
            System.exit(1);
        }
    }

    //@1ejun6 > integration flow > load > extract > validate > grade > clean up
    //@1ejun6 > result map keeps each submission together with its question grades
    public static Map<StudentSubmission, List<QuestionGrade>> run(Config config) throws IOException {
        //@1ejun6 > 1 > load question requirements & teacher provided tester programs
        var assessment = AssessmentLoader.load(config.getResourcesDirectory());
        var testers = TesterLoader.load(config.getTestersDirectory());
        Path workspace = Files.createTempDirectory("autograder-submissions-");
        Map<StudentSubmission, List<QuestionGrade>> results;
        try {
            //@1ejun6 > logs & extracted submissions stay outside original project files
            //@1ejun6 > 2 > read class list used to identify who submitted each zip
            //the scoresheet may not be provided > build it from the zips first
            if (ScoreSheet.createIfMissing(config.getScoresheetFile(), config.getSubmissionsDirectory())) {
                System.out.println("scoresheet not found > created " + config.getScoresheetFile());
            }
            var students = new StudentCsvReader().read(config.getScoresheetFile(), workspace.resolve("csv.log.jsonl"));
            if (students.isEmpty()) throw new IOException("no valid students found in scoresheet");

            //@1ejun6 > 3 > wire validation rules together using same file finder
            //@1ejun6 > checks collect issues before answers reach grader
            var finder = new SubmissionFileFinder();
            var validator = new SubmissionValidator(List.of(new AnswerFileRule(), new StudentHeaderRule()),
                    List.of(new UnexpectedFileRule()), finder);

            //@1ejun6 > 4 > extract zips > validate each extracted submission
            //@1ejun6 > validation creates ~questionattempt~ objects consumed by grading
            var submissions = SubmissionLoader.loadSubmissions(config.getSubmissionsDirectory(), workspace, students, assessment);
            if (submissions.isEmpty()) throw new IOException("no submission ZIP files found");
            for (var submission : submissions) {
                validator.validate(submission, assessment);
            }

            //@1ejun6 > 5 > pass prepared submissions to grading > each question gets its answer & requirements & tester & compilation/execution limits.
            results = new AssessmentGrader(new QuestionGrader(config.getCompilationTimeout(), config.getExecutionTimeout())).grade(submissions, assessment, testers);
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
                if (grade.getStatus() != GradeStatus.COMPLETED) System.out.println("    " + grade.getMessage());
            }
            System.out.println("  total > " + entry.getValue().stream().mapToDouble(QuestionGrade::getEarnedMarks).sum());
        }
    }
}
