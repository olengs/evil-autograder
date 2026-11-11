package grading;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import assessment.AssessmentDefinition;
import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import grading.progress.ProgressListener;
import submission.QuestionAttempt;
import submission.StudentSubmission;
import utils.logging.JsonLineLogger;
import utils.logging.JsonLineLogger.Status;
import utils.process.Interruptions;
import utils.process.ProcessExecutionException;
import validation.SubmissionIssue;

/// grades each submission in question order & records skipped questions
public class AssessmentGrader { 
    private final int workerCount;
    private final ThreadLocal<QuestionGrader> questionGrader;

    public AssessmentGrader(int count, Duration compilationTimeout, Duration executionTimeout) {

        this(count, compilationTimeout, executionTimeout, utils.process.ProcessExecutor.DEFAULT_OUTPUT_MAX_BYTES);
    }

    public AssessmentGrader(int count, Duration compilationTimeout, Duration executionTimeout, long outputMaxBytes) {
        this.workerCount = count;
        questionGrader = ThreadLocal.withInitial(() -> new QuestionGrader(compilationTimeout, executionTimeout, outputMaxBytes));
    }

    public Map<StudentSubmission, List<QuestionGrade>> grade(List<StudentSubmission> submissions, 
            AssessmentDefinition assessment, Map<String, TesterDefinition> testers, 
            ProgressListener progress) throws IOException {
        
        //keep submission order so console reports & verification are predictable
        Map<StudentSubmission, List<QuestionGrade>> results = new LinkedHashMap<>();
        List<String> questionIds = assessment.getQuestions().stream().map(QuestionDefinition::getId).toList();
        progress.batchStarted(submissions.size(), questionIds);

        try (ExecutorService workers = Executors.newFixedThreadPool(workerCount)) {
            List<Future<List<QuestionGrade>>> futures = new ArrayList<>();
            // submit each student for grading
            try {
                for (StudentSubmission submission : submissions) {
                    futures.add(workers.submit(() -> {
                        ProgressListener studentProgress = progress.forSubmission(
                                submission.getOriginalZip().toAbsolutePath().normalize().toString(),
                                submission.getDisplayName());
                        studentProgress.submissionStarted(submission.getDisplayName());
                        try {
                            var grades = gradeSubmission(submission, assessment, testers, studentProgress);
                            studentProgress.submissionFinished();
                            return grades;
                        } catch (IOException | RuntimeException | Error failure) {
                            studentProgress.submissionAborted();
                            throw failure;
                        }
                    }));
                }

                //collect in order for predictable output
                for (int i = 0; i < submissions.size(); i++) {
                    StudentSubmission submission = submissions.get(i);
                    List<QuestionGrade> grades = futures.get(i).get();
                    results.put(submission, grades);

                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                var interrupted = new InterruptedIOException("grading interrupted");
                interrupted.initCause(e);
                throw interrupted;
            } catch (ExecutionException e) {
                throw new IOException("grading worker failed", e.getCause());
            } finally {
                for (var future : futures) {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                }
                workers.shutdownNow();
            }
            
        }
        return results;
    }

    private List<QuestionGrade> gradeSubmission(StudentSubmission submission, AssessmentDefinition assessment, Map<String, TesterDefinition> testers, ProgressListener progress) throws IOException {
        List<QuestionGrade> grades = new ArrayList<>();
        String archive = submission.getOriginalZip().toAbsolutePath().normalize().toString();
        String identity = submission.getStudent() == null ? "" : submission.getStudent().getUsername();
        String studentLabel = "[student=" + (identity == null || identity.isBlank() ? archive : identity)
                + "][archive=" + archive + "]";
        for (QuestionDefinition question : assessment.getQuestions()) {
            Interruptions.check("grading interrupted");
            progress.questionStarted(question.getId());
            TesterDefinition tester = testers.get(question.getId().toLowerCase(Locale.ROOT));
            QuestionGrade grade = gradeOrSkipQuestion(submission, question, tester, progress, studentLabel);
            logQuestionSummary(studentLabel, grade, tester);
            grades.add(grade);
            progress.questionFinished(question.getId(), grade.getStatus());
        }
        return grades;
    }

    private QuestionGrade gradeOrSkipQuestion(StudentSubmission submission, QuestionDefinition question,
            TesterDefinition tester, ProgressListener progress, String studentLabel) throws IOException {
        
        QuestionAttempt attempt = submission.findAttempt(question.getId()).orElse(null);
        if (attempt == null) {
            return new QuestionGrade(question.getId(), GradeStatus.skipped, 0, List.of(), 
                "question not attempted");
        }
        String skipReason = findBlockingIssue(submission, question.getId());

        //keep first reason > reported errors & missing attempt & invalid attempt > then missing tester
        if (skipReason == null && !attempt.isValid()) { skipReason = "attempt has validation errors"; }
        if (skipReason == null && tester == null) { skipReason = "missing tester"; }
        if (skipReason != null) {
            return new QuestionGrade(question.getId(), GradeStatus.skipped, 0, List.of(), skipReason);
        }
        try {
            return questionGrader.get().grade(question, attempt, tester, progress, studentLabel);
        } catch (IOException | ProcessExecutionException error) {
            if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
            JsonLineLogger.getInstance().logException(Status.ERROR, "AssessmentGrader",
                    studentLabel + " " + question.getId() + " > question grading failed", error);
            return new QuestionGrade(question.getId(), GradeStatus.runtimeError, 0, List.of(),
                    "question grading failed; see diagnostic log");
        }
    }

    // One summary per question, including skipped questions and partial timeout results.
    private void logQuestionSummary(String studentLabel, QuestionGrade grade, TesterDefinition tester) {
        long passed = grade.getTestCaseResults().stream().filter(TestCaseResult::isPassed).count();
        int completed = grade.getTestCaseResults().size();
        int expected = tester == null ? 0 : tester.getNumTestCases();
        if (expected == 0 && grade.getStatus() == GradeStatus.completed) expected = completed;
        String cases = expected > 0 ? passed + "/" + expected + " test cases passed"
                : passed + " test cases passed; expected total unknown";
        String marks = Double.toString(grade.getEarnedMarks())
                + (tester == null ? " (maximum unknown)" : "/" + tester.getMaximumMarks());
        Status level = switch (grade.getStatus()) {
            case completed -> Status.LOG;
            case skipped, timeout -> Status.WARNING;
            default -> Status.ERROR;
        };
        JsonLineLogger.getInstance().append(level, "AssessmentGrader",
                studentLabel + " " + grade.getQuestionId() + " > " + grade.getStatus()
                + " > " + cases + " > " + completed + " completed > marks " + marks
                + " > " + grade.getMessage());
    }

    private String findBlockingIssue(StudentSubmission submission, String questionId) {
        //warnings allow grading > an error without a question id blocks entire submission
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            if (issue.blocksQuestion(questionId)) return issue.getMessage();
        }
        return null;
    }
}
