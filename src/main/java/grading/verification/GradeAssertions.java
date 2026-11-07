//checks whether a question's grade agrees with its validation findings & process evidence
//shares status & mark-total assertions across runtime checks for every student question

package grading.verification;

import grading.GradeStatus;
import grading.QuestionGrade;
import static org.junit.jupiter.api.Assertions.*;
import submission.QuestionAttempt;
import submission.StudentSubmission;

final class GradeAssertions {
    private GradeAssertions() {}

    static void check(StudentSubmission submission, QuestionGrade grade, boolean hasTester) {
        assertTrue(Double.isFinite(grade.getEarnedMarks()) && grade.getEarnedMarks() >= 0, "marks must be finite and nonnegative");
        QuestionAttempt attempt = submission.findAttempt(grade.getQuestionId()).orElse(null);

        boolean blocked = !hasTester || attempt == null || !attempt.isValid();
        for (var issue : submission.getSubmissionIssues()) {
            if (issue.blocksQuestion(grade.getQuestionId())) {
                blocked = true;
                break;
            }
        }
        assertEquals(blocked, grade.getStatus() == GradeStatus.skipped, "only questions blocked by validation or a missing tester should be skipped");

        double total = 0;
        for (var testCase : grade.getTestCaseResults()) {
            assertTrue(Double.isFinite(testCase.getEarnedMarks()) && testCase.getEarnedMarks() >= 0);
            total += testCase.getEarnedMarks();
        }
        assertEquals(total, grade.getEarnedMarks(), 1e-9, "marks must equal recorded case totals");

        switch (grade.getStatus()) {
            case skipped -> {
                assertEquals(0, grade.getEarnedMarks());
                assertTrue(grade.getTestCaseResults().isEmpty());
                assertTrue(grade.getProcessResult().isEmpty());
                assertFalse(grade.getMessage().isBlank());
            }
            case completed -> {
                assertTrue(grade.getProcessResult().orElseThrow().isSuccessful());
                assertFalse(grade.getTestCaseResults().isEmpty());
            }
            case compileError, runtimeError -> {
                var process = grade.getProcessResult().orElseThrow();
                assertFalse(process.isSuccessful());
                assertFalse(process.isTimedOut());
                assertEquals(0, grade.getEarnedMarks());
                assertTrue(grade.getTestCaseResults().isEmpty());
            }
            case timeout -> {
                var process = grade.getProcessResult().orElseThrow();
                assertTrue(process.isTimedOut());
                assertFalse(process.isSuccessful());
            }
        }
    }
}
