package grading;

import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import java.io.IOException;
import grading.progress.ProgressListener;
import submission.QuestionAttempt;

@FunctionalInterface
public interface QuestionGrading { //defines how an eligible question attempt is graded
    QuestionGrade grade(QuestionDefinition question, QuestionAttempt attempt, TesterDefinition tester, ProgressListener progress) throws IOException;
}
