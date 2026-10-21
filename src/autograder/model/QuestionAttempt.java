package autograder.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionAttempt {
    private final QuestionDefinition questionDefinition;
    private final List<Path> answerFiles;
    private final List<SubmissionIssue> issues;

    public QuestionAttempt(QuestionDefinition questionDefinition, List<Path> answerFiles, List<SubmissionIssue> issues) {
        this.questionDefinition = questionDefinition;
        this.answerFiles = new ArrayList<>(answerFiles);
        this.issues = new ArrayList<>(issues);
    }

    public QuestionDefinition getQuestionDefinition() {
        return questionDefinition;
    }

    public List<Path> getAnswerFiles() {
        return Collections.unmodifiableList(answerFiles);
    }

    public List<SubmissionIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean isValid() {
        return !issues.stream().anyMatch(x -> x.getSeverity() == IssueSeverity.ERROR);
    }
}