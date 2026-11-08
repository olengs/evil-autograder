package grading;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import assessment.AssessmentDefinition;
import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import grading.progress.ProgressListener;
import submission.QuestionAttempt;
import submission.StudentSubmission;
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;
import utils.process.Interruptions;
import utils.process.ProcessResult;
import validation.SubmissionIssue;

public class AssessmentGrader { //grades each submission in question order & records skipped questions
    private final QuestionGrading questionGrader;

    public AssessmentGrader(QuestionGrading questionGrader) {
        this.questionGrader = questionGrader;
    }

    public Map<StudentSubmission, List<QuestionGrade>> grade(List<StudentSubmission> submissions, AssessmentDefinition assessment, Map<String, TesterDefinition> testers, ProgressListener progress) throws IOException {
        //keep submission order so console reports & verification are predictable
        Map<StudentSubmission, List<QuestionGrade>> results = new LinkedHashMap<>();
        List<String> questionIds = assessment.getQuestions().stream().map(QuestionDefinition::getId).toList();
        progress.batchStarted(submissions.size(), questionIds);
        for (StudentSubmission submission : submissions) {
            Interruptions.check("grading interrupted");
            progress.submissionStarted(submission.getDisplayName());
            results.put(submission, gradeSubmission(submission, assessment, testers, progress));
            progress.submissionFinished();
        }
        return results;
    }

    private List<QuestionGrade> gradeSubmission(StudentSubmission submission, AssessmentDefinition assessment, Map<String, TesterDefinition> testers, ProgressListener progress) throws IOException {
        List<QuestionGrade> grades = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            Interruptions.check("grading interrupted");
            progress.questionStarted(question.getId());
            TesterDefinition tester = testers.get(question.getId().toLowerCase(Locale.ROOT));
            QuestionGrade grade = gradeOrSkipQuestion(submission, question, tester, progress);
            grades.add(grade);
            progress.questionFinished(question.getId(), grade.getStatus());
        }
        return grades;
    }

    private QuestionGrade gradeOrSkipQuestion(StudentSubmission submission, QuestionDefinition question,
            TesterDefinition tester, ProgressListener progress) throws IOException {
        
        QuestionAttempt attempt = submission.findAttempt(question.getId()).orElse(null);
        if (attempt == null) {
            JsonLineLogger.getInstance().append(Status.WARNING, "AssessmentGrader", 
                String.format("Skipped grading for [%s] because attempt not found for question [%s]", submission.getStudent().getUsername(), question.getId()));

            return new QuestionGrade(question.getId(), GradeStatus.skipped, 0, List.of(), 
                new ProcessResult(1, "", "", false), "question not attempted");
        }
        String skipReason = findBlockingIssue(submission, question.getId());

        //keep first reason > reported errors & missing attempt & invalid attempt > then missing tester
        if (skipReason == null && !attempt.isValid()) { skipReason = "attempt has validation errors"; }
        if (skipReason == null && tester == null) { skipReason = "missing tester"; }
        if (skipReason != null) {
            return new QuestionGrade(question.getId(), GradeStatus.skipped, 0, List.of(), null, skipReason);
        }
        try {
            return questionGrader.grade(question, attempt, tester, progress);
        } catch (IOException error) {
            if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
            return new QuestionGrade(question.getId(), GradeStatus.runtimeError, 0, List.of(), new ProcessResult(1, "", "", false),
                    "question grading failed > " + error.getMessage());
        }
    }

    private String findBlockingIssue(StudentSubmission submission, String questionId) {
        //warnings allow grading > an error without a question id blocks entire submission
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            if (issue.blocksQuestion(questionId)) return issue.getMessage();
        }
        return null;
    }
}
