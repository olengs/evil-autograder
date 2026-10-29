package grading;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import model.*;

public class AssessmentGrader {
    private final QuestionGrader questionGrader;

    public AssessmentGrader(QuestionGrader questionGrader) {
        this.questionGrader = questionGrader;
    }

    public Map<StudentSubmission, List<QuestionGrade>> grade(List<StudentSubmission> submissions, AssessmentDefinition assessment, Map<String, TesterDefinition> testers) throws IOException {
        Map<StudentSubmission, List<QuestionGrade>> results = new LinkedHashMap<>();
        for (StudentSubmission submission : submissions) {
            List<QuestionGrade> grades = new ArrayList<>();
            for (QuestionDefinition question : assessment.getQuestions()) {
                String skipReason = findBlockingIssue(submission, question.getId());
                QuestionAttempt attempt = findAttempt(submission, question.getId());
                TesterDefinition tester = testers.get(question.getId().toLowerCase(Locale.ROOT));
                if (skipReason == null && attempt == null) skipReason = "no validated attempt";
                if (skipReason == null && !attempt.isValid()) skipReason = "attempt has validation errors";
                if (skipReason == null && tester == null) skipReason = "missing tester";

                if (skipReason != null) {
                    grades.add(new QuestionGrade(question.getId(), GradeStatus.SKIPPED, 0, List.of(), null, skipReason));
                } else {
                    grades.add(questionGrader.grade(question, attempt, tester));
                }
            }
            results.put(submission, grades);
        }
        return results;
    }

    private QuestionAttempt findAttempt(StudentSubmission submission, String questionId) {
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            if (attempt.getQuestionDefinition().getId().equals(questionId)) return attempt;
        }
        return null;
    }

    private String findBlockingIssue(StudentSubmission submission, String questionId) {
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            if (issue.getSeverity() == IssueSeverity.ERROR && (issue.getQuestionId() == null || issue.getQuestionId().equals(questionId))) {
                return issue.getMessage();
            }
        }
        return null;
    }
}