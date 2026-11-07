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
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import report.ScoreSheetCreator;
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
        AssessmentDefinition assessment = AssessmentLoader.load(config.getResourcesDirectory());
        Map<String, TesterDefinition> testers = TesterLoader.load(config.getTestersDirectory());

        //extracted answers & student list logs live only for this run > including when a step fails
        try (var workspace = new TemporaryDirectory("autograder-submissions-")) {
            List<StudentDetails> roster = prepareRoster(config, workspace.path());
            List<StudentSubmission> submissions = SubmissionLoader.loadSubmissions(config.getSubmissionsDirectory(), workspace.path(), roster, assessment);
           
            if (submissions.isEmpty()) throw new IOException("no submission ZIP files found");

            SubmissionValidator validator = createValidator();
            for (StudentSubmission submission : submissions) validator.validate(submission, assessment);

            var questionGrader = new QuestionGrader(config.getCompilationTimeout(), config.getExecutionTimeout());
            return new AssessmentGrader(questionGrader).grade(submissions, assessment, testers);
        }
    }

    private static List<StudentDetails> prepareRoster(Config config, Path workspace) throws IOException {
        if (ScoreSheetCreator.createIfMissing(config.getScoresheetFile(), config.getSubmissionsDirectory())) System.out.println("scoresheet not found > created " + config.getScoresheetFile());
       
        List<StudentDetails> roster = new StudentRosterReader().read(config.getScoresheetFile(), workspace.resolve("csv.log.jsonl"));
        if (roster.isEmpty()) throw new IOException("no valid students found in scoresheet");
        return roster;
    }

    private static SubmissionValidator createValidator() {
        return new SubmissionValidator(List.of(new AnswerFileRule(), new StudentHeaderRule()), List.of(new UnexpectedFileRule()), new SubmissionFileFinder());
    }
}
