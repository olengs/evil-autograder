//produces an ordered grade for every question in every student submission
//explains skipped questions & passes eligible attempts to questiongrader for compilation & testing

package grading;

import assessment.AssessmentDefinition;
import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import submission.QuestionAttempt;
import submission.StudentSubmission;
import validation.SubmissionIssue;

public class AssessmentGrader {
    private final QuestionGrading questionGrader;

    public AssessmentGrader(QuestionGrading questionGrader) {
        this.questionGrader = questionGrader;
    }

    public Map<StudentSubmission, List<QuestionGrade>> grade(List<StudentSubmission> submissions, AssessmentDefinition assessment, Map<String, TesterDefinition> testers) throws IOException {
        //keep submission order so console reports & verification are predictable
        Map<StudentSubmission, List<QuestionGrade>> results = new LinkedHashMap<>();
        for (StudentSubmission submission : submissions) {
            results.put(submission, gradeSubmission(submission, assessment, testers));
        }
        return results;
    }

    private List<QuestionGrade> gradeSubmission(StudentSubmission submission, AssessmentDefinition assessment, Map<String, TesterDefinition> testers) throws IOException {
        List<QuestionGrade> grades = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            TesterDefinition tester = testers.get(question.getId().toLowerCase(Locale.ROOT));
            grades.add(gradeOrSkipQuestion(submission, question, tester));
        }
        return grades;
    }

    private QuestionGrade gradeOrSkipQuestion(StudentSubmission submission, QuestionDefinition question,
            TesterDefinition tester) throws IOException {
        QuestionAttempt attempt = submission.findAttempt(question.getId()).orElse(null);
        String skipReason = findBlockingIssue(submission, question.getId());

        //keep first reason > reported errors & missing attempt & invalid attempt > then missing tester
        if (skipReason == null && attempt == null) { skipReason = "no validated attempt"; }
        if (skipReason == null && !attempt.isValid()) { skipReason = "attempt has validation errors"; }
        if (skipReason == null && tester == null) { skipReason = "missing tester"; }
        if (skipReason != null) return new QuestionGrade(question.getId(), GradeStatus.skipped, 0, List.of(), null, skipReason);
        return questionGrader.grade(question, attempt, tester);
    }

    private String findBlockingIssue(StudentSubmission submission, String questionId) {
        //warnings allow grading > an error without a question id blocks entire submission
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            if (issue.blocksQuestion(questionId)) return issue.getMessage();
        }
        return null;
    }
}
