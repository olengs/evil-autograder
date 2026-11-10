package app;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import assessment.AssessmentDefinition;
import assessment.AssessmentLoader;
import assessment.TesterDefinition;
import assessment.TesterLoader;
import config.Config;
import grading.AssessmentGrader;
import grading.QuestionGrade;
import grading.progress.ProgressListener;
import submission.StudentDetails;
import submission.StudentRosterBuilder;
import submission.StudentSubmission;
import submission.SubmissionLoader;
import submission.files.SubmissionFileFinder;
import utils.files.TemporaryDirectory;
import utils.process.Interruptions;
import validation.SubmissionValidator;
import validation.rules.AnswerFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

public final class Autograder { //coordinates assessment loading, submission validation & grading
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
                List<StudentDetails> roster = prepareRoster(config);
                progress.preparing("extracting submission zip files");
                List<StudentSubmission> submissions = SubmissionLoader.loadSubmissions(config.getSubmissionsDirectory(), workspace.path(), roster, assessment, config.getHeaderSearchDepth());
                if (submissions.isEmpty()) throw new IOException("no submission ZIP files found");

                SubmissionValidator validator = createValidator(config.getAnswerSearchDepth());
                for (StudentSubmission submission : submissions) {
                    Interruptions.check("validation interrupted");
                    progress.validationStarted(submission.getDisplayName());
                    validator.validate(submission, assessment);
                }

                var assessmentGrader = new AssessmentGrader(config.getNumGradingWorkers(), config.getCompilationTimeout(), config.getExecutionTimeout(), config.getProcessOutputMaxBytes());
                return assessmentGrader.grade(submissions, assessment, testers, progress);
            } finally {
                progress.preparing("cleaning up temporary files");
            }
        }
    }

    private static List<StudentDetails> prepareRoster(Config config) throws IOException {
        List<StudentDetails> roster = StudentRosterBuilder.build(config.getSubmissionsDirectory());
        if (roster.isEmpty()) throw new IOException("no valid students found in scoresheet");
        return roster;
    }

    private static SubmissionValidator createValidator(int answerSearchDepth) {
        return new SubmissionValidator(List.of(new AnswerFileRule(), new StudentHeaderRule()), List.of(new UnexpectedFileRule()), new SubmissionFileFinder(answerSearchDepth));
    }
}
