//coordinates assessment loading & submission preparation & validation & grading
//returns each submission with its question grades after cleaning up temporary extraction workspace

package app;

import assessment.AssessmentDefinition;
import assessment.AssessmentLoader;
import assessment.TesterDefinition;
import assessment.TesterLoader;
import config.Config;
import grading.AssessmentGrader;
import grading.QuestionGrade;
import grading.QuestionGrader;
import utils.files.TemporaryDirectory;
import utils.process.Interruptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import report.ScoreSheetCreator;
import grading.progress.ProgressListener;
import submission.StudentDetails;
import submission.StudentRosterReader;
import submission.StudentSubmission;
import submission.SubmissionLoader;
import submission.files.SubmissionFileFinder;
import validation.SubmissionValidator;
import validation.rules.AnswerFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

public final class Autograder {
    private Autograder() {}

    public static Map<StudentSubmission, List<QuestionGrade>> run(Config config) throws IOException {
        return run(config, ProgressListener.NONE);
    }

    public static Map<StudentSubmission, List<QuestionGrade>> run(Config config, ProgressListener progress) throws IOException {
        try {
            Map<StudentSubmission, List<QuestionGrade>> results = runAssessment(config, progress);
            progress.batchFinished(); //temporary workspaces have closed before reporting completion
            return results;
        } catch (IOException | RuntimeException | Error failure) {
            progress.failed(failure.getMessage() == null ? failure.toString() : failure.getMessage());
            throw failure;
        }
    }

    private static Map<StudentSubmission, List<QuestionGrade>> runAssessment(Config config, ProgressListener progress) throws IOException {
        progress.preparing("loading assessment & testers");
        AssessmentDefinition assessment = AssessmentLoader.load(config.getResourcesDirectory());
        Map<String, TesterDefinition> testers = TesterLoader.load(config.getTestersDirectory());

        //extracted answers & student list logs live only for this run > including when a step fails
        try (var workspace = new TemporaryDirectory("autograder-submissions-")) {
            try {
                progress.preparing("reading student scoresheet");
                List<StudentDetails> roster = prepareRoster(config, workspace.path(), progress);
                progress.preparing("extracting submission zip files");
                List<StudentSubmission> submissions = SubmissionLoader.loadSubmissions(config.getSubmissionsDirectory(), workspace.path(), roster, assessment);
                if (submissions.isEmpty()) throw new IOException("no submission ZIP files found");

                SubmissionValidator validator = createValidator();
                for (StudentSubmission submission : submissions) {
                    Interruptions.check("validation interrupted");
                    progress.validationStarted(submission.getStudent() == null ? submission.getOriginalZip().getFileName().toString() : submission.getStudent().getUsername());
                    validator.validate(submission, assessment);
                }

                var questionGrader = new QuestionGrader(config.getCompilationTimeout(), config.getExecutionTimeout());
                return new AssessmentGrader(questionGrader).grade(submissions, assessment, testers, progress);
            } finally {
                progress.preparing("cleaning up temporary files");
            }
        }
    }

    private static List<StudentDetails> prepareRoster(Config config, Path workspace, ProgressListener progress) throws IOException {
        if (ScoreSheetCreator.createIfMissing(config.getScoresheetFile(), config.getSubmissionsDirectory())) {
            String message = "scoresheet not found > created " + config.getScoresheetFile();
            progress.preparing(message);
            if (progress == ProgressListener.NONE) System.out.println(message);
        }
       
        List<StudentDetails> roster = new StudentRosterReader().read(config.getScoresheetFile(), workspace.resolve("csv.log.jsonl"));
        if (roster.isEmpty()) throw new IOException("no valid students found in scoresheet");
        return roster;
    }

    private static SubmissionValidator createValidator() {
        return new SubmissionValidator(List.of(new AnswerFileRule(), new StudentHeaderRule()), List.of(new UnexpectedFileRule()), new SubmissionFileFinder());
    }
}
