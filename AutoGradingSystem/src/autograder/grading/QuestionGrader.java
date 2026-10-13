package autograder.grading;

import autograder.model.QuestionDefinition;
import autograder.model.TesterDefinition;
import autograder.model.QuestionAttempt;
import autograder.model.ProcessResult;
import autograder.model.QuestionGrade;
import autograder.workspace.WorkspacePreparer;
import autograder.runner.ProcessRunner;

import java.nio.file.Path;

/**
 * QuestionGrader is a interface with a static method, {@code grade} that grades a student attempt.
 */
public interface QuestionGrader {
    /**
     * Single static method that combine the responsibility of creating workspace, 
     * compile files, execute files, and give the attemp a grade. This method 
     * assums that {@code QuestionAttempt} has {@code QuestionDefination} and 
     * {@code TestDefination}.
     * 
     * @param attempt Student attemp of a question
     * @return {@code QuestionGrade} of a particular question attemp
     */
    // assume attempt has question defination and tester defination
    static QuestionGrade grade(QuestionAttempt attempt) {
        QuestionDefinition question = attempt.getQuestionDefination();
        ProcessResult processResult = null;

        // start the workspace only if attemp is valid
        if (attempt.isValid()) {
            try (WorkspacePreparer prep = new WorkspacePreparer()) {
                Path ws = prep.prepare(question, attempt);

                // TODO: not very good to hardcode
                ProcessRunner runner = new ProcessRunner(java.time.Duration.ofSeconds(10));
                processResult = runner.compile(ws);
            }
        }

        ProcessGrader grader = new ProcessGrader();
        // provide the attemp a grade, regardless if the there is error during workspace preparation, compile code, run code or timeout
        return grader.gradeQuestion(question.getId(), processResult);
    }
}
