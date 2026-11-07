//defines operation used to grade an eligible question attempt
//allows assessment grading to use process-based grader or a supplied implementation

package grading;

import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import java.io.IOException;
import grading.progress.ProgressListener;
import submission.QuestionAttempt;

@FunctionalInterface
public interface QuestionGrading {
    QuestionGrade grade(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester) throws IOException;

    default QuestionGrade grade(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester, ProgressListener progress) throws IOException {
        return grade(question, attempt, tester);
    }
}
