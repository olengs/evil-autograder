package grading;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import model.AssessmentDefinition;
import model.GradeStatus;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.QuestionGrade;
import model.StudentSubmission;
import model.SubmissionIssue;
import model.TesterDefinition;
import progress.ProgressListener;

public class AssessmentGrader {
    private final QuestionGrader questionGrader;

    public AssessmentGrader(QuestionGrader questionGrader) {
        this.questionGrader = questionGrader;
    }

    //@1ejun6 > connect validated submissions to QuestionGrader > handle one question at a time
    public Map<StudentSubmission, List<QuestionGrade>> grade(List<StudentSubmission> submissions, AssessmentDefinition assessment,
            Map<String, TesterDefinition> testers, ProgressListener progress) throws IOException {
        //@1ejun6 > keep student results in submission order for reporting
        Map<StudentSubmission, List<QuestionGrade>> results = new LinkedHashMap<>();
        List<String> questionIds = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) questionIds.add(question.getId());
        progress.batchStarted(submissions.size(), questionIds);

        for (StudentSubmission submission : submissions) {
            checkInterrupted();
            progress.submissionStarted(submission.getStudent() == null ? submission.getOriginalZip().getFileName().toString() : submission.getStudent().getUsername());
            List<QuestionGrade> grades = new ArrayList<>();

            for (QuestionDefinition question : assessment.getQuestions()) {
                checkInterrupted();
                progress.questionStarted(question.getId());
                //@1ejun6 > 1 > match this question with its validated attempt & tester
                String skipReason = findBlockingIssue(submission, question.getId());
                QuestionAttempt attempt = findAttempt(submission, question.getId());
                TesterDefinition tester = testers.get(question.getId().toLowerCase(Locale.ROOT));

                //TODO: handle case where attempt is null.

                //@1ejun6 > 2 > keep whichever blocking reason appears first
                //@1ejun6 > check for a missing attempt before reading its validation status
                if (skipReason == null && attempt == null) { skipReason = "no validated attempt"; }
                if (skipReason == null && !attempt.isValid()) { skipReason = "attempt has validation errors"; }
                if (skipReason == null && tester == null) { skipReason = "missing tester"; }

                //@1ejun6 > 3 > record a result even when a question is skipped
                //@1ejun6 > otherwise pass its answer to QuestionGrader > compile & run tests & calculate marks
                if (skipReason != null) {
                    grades.add(new QuestionGrade(question.getId(), GradeStatus.skipped, 0, List.of(), null, skipReason));
                } else {
                    grades.add(questionGrader.grade(question, attempt, tester, progress));
                }
                progress.questionFinished(question.getId(), grades.getLast().getStatus());
            }
            results.put(submission, grades);
            progress.submissionFinished();
        }
        return results;
    }

    private void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("grading interrupted");
        }
    }

    //@1ejun6 > find this question attempt among attempts saved during validation
    private QuestionAttempt findAttempt(StudentSubmission submission, String questionId) {
        for (QuestionAttempt attempt : submission.getQuestionAttempts()) {
            if (attempt.getQuestionDefinition().getId().equals(questionId)) {
                return attempt;
            }
        }

        return null;
    }

    //@1ejun6 > errors without a question id block grading for this submission
    //@1ejun6 > errors with a question id block only that question > warnings allow grading
    private String findBlockingIssue(StudentSubmission submission, String questionId) {
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            boolean iserror = issue.getSeverity() == IssueSeverity.ERROR;
            boolean affectsquestion = issue.getQuestionId() == null || issue.getQuestionId().equals(questionId);

            if (iserror && affectsquestion) { return issue.getMessage(); }
        }

        return null;
    }
}
