package junit;

import static org.junit.jupiter.api.Assertions.*;

import model.GradeStatus;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionGrade;
import model.StudentSubmission;

final class GradeAssertions { //shared consistency checks for a single grading result
    private GradeAssertions() {}

    static void check(StudentSubmission submission, QuestionGrade grade, boolean hasTester) {
        assertTrue(Double.isFinite(grade.getEarnedMarks()) && grade.getEarnedMarks() >= 0,
                "marks must be finite and nonnegative");
        QuestionAttempt attempt = null;
        for (QuestionAttempt candidate : submission.getQuestionAttempts()) {
            if (candidate.getQuestionDefinition().getId().equals(grade.getQuestionId())) {
                attempt = candidate;
                break;
            }
        }

        boolean blocked = !hasTester || attempt == null || !attempt.isValid();
        for (var issue : submission.getSubmissionIssues()) {
            boolean isError = issue.getSeverity() == IssueSeverity.ERROR;
            boolean affectsQuestion = issue.getQuestionId() == null || issue.getQuestionId().equals(grade.getQuestionId());
            if (isError && affectsQuestion) {
                blocked = true;
                break;
            }
        }
        assertEquals(blocked, grade.getStatus() == GradeStatus.SKIPPED,
                "only questions blocked by validation or a missing tester should be skipped");

        double total = 0;
        for (var testCase : grade.getTestCaseResults()) {
            assertTrue(Double.isFinite(testCase.getEarnedMarks()) && testCase.getEarnedMarks() >= 0);
            total += testCase.getEarnedMarks();
        }
        assertEquals(total, grade.getEarnedMarks(), 1e-9, "marks must equal recorded case totals");

        switch (grade.getStatus()) {
            case SKIPPED -> {
                assertEquals(0, grade.getEarnedMarks());
                assertTrue(grade.getTestCaseResults().isEmpty());
                assertTrue(grade.getProcessResult().isEmpty());
                assertFalse(grade.getMessage().isBlank());
            }
            case COMPLETED -> {
                assertTrue(grade.getProcessResult().orElseThrow().isSuccessful());
                assertFalse(grade.getTestCaseResults().isEmpty());
            }
            case COMPILE_ERROR, RUNTIME_ERROR -> {
                var process = grade.getProcessResult().orElseThrow();
                assertFalse(process.isSuccessful());
                assertFalse(process.isTimedOut());
                assertEquals(0, grade.getEarnedMarks());
                assertTrue(grade.getTestCaseResults().isEmpty());
            }
            case TIMEOUT -> {
                var process = grade.getProcessResult().orElseThrow();
                assertTrue(process.isTimedOut());
                assertFalse(process.isSuccessful());
            }
            case PENDING -> fail("grading must finish before its result is checked");
        }
    }
}
