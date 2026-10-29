package model;

public class QuestionTestPack {
    private final QuestionDefinition questionDefinition;
    private final QuestionAttempt questionAttempt;
    private final TesterDefinition testerDefinition;

    /// Assumes that questionDefinition is validated or null, questionAttempt is validated or null, testerDefinition is validated or null
    public QuestionTestPack(QuestionDefinition questionDefinition, QuestionAttempt questionAttempt, TesterDefinition testerDefinition) {
        this.questionDefinition = questionDefinition;
        this.questionAttempt = questionAttempt;
        this.testerDefinition = testerDefinition;
        validateInputs();
    }

    private void validateInputs() {
        if (questionDefinition == null || questionAttempt == null || testerDefinition == null) {
            throw new IllegalArgumentException("null grading defintions received");
        }
    }

    public QuestionDefinition getQuestionDefinition() {
        return questionDefinition;
    }

    public QuestionAttempt getQuestionAttempt() {
        return questionAttempt;
    }

    public TesterDefinition getTesterDefinition() {
        return testerDefinition;
    }
}
