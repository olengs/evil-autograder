package grading.verification;

import grading.GradeStatus;
import grading.QuestionGrade;
import static org.junit.jupiter.api.Assertions.*;
import submission.QuestionAttempt;
import submission.StudentSubmission;

final class GradeAssertions { //checks question grades against validation findings & tester output
    private GradeAssertions() {}

    static void check(StudentSubmission submission, QuestionGrade grade, boolean hasTester) {
        assertTrue(Double.isFinite(grade.getEarnedMarks()) && grade.getEarnedMarks() >= 0, "marks must be finite and nonnegative");
        QuestionAttempt attempt = submission.findAttempt(grade.getQuestionId()).orElse(null);
        assertNotNull(attempt, "grade must have a matching question attempt");

        boolean blocked = !attempt.isValid();
        for (var issue : submission.getSubmissionIssues()) {
            if (issue.blocksQuestion(grade.getQuestionId())) {
                blocked = true;
                break;
            }
        }
        assertEquals(blocked || !hasTester, grade.getStatus() == GradeStatus.skipped, "only questions blocked by validation or a missing tester should be skipped");

        double total = 0;
        for (var testCase : grade.getTestCaseResults()) {
            assertTrue(Double.isFinite(testCase.getEarnedMarks()) && testCase.getEarnedMarks() >= 0);
            total += testCase.getEarnedMarks();
        }
        assertEquals(total, grade.getEarnedMarks(), "marks must equal recorded case totals");

        switch (grade.getStatus()) {
            case skipped -> {
                assertTrue(blocked || grade.getMessage().equals("missing tester"), grade.getMessage());
                assertEquals(0, grade.getEarnedMarks());
                assertTrue(grade.getTestCaseResults().isEmpty());
                assertTrue(grade.getProcessResult().isEmpty());
                assertFalse(grade.getMessage().isBlank());
            }
            case completed -> {
                var process = grade.getProcessResult().orElseThrow();
                assertTrue(process.isSuccessful());
                assertFalse(grade.getTestCaseResults().isEmpty());
                String printedTotal = process.getStandardOutput().strip().lines().toList().getLast();
                assertEquals(Double.parseDouble(printedTotal), grade.getEarnedMarks(), "marks must match tester's printed total");
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
                long passedCases = process.getStandardOutput().lines().filter(line -> line.strip().equalsIgnoreCase("Passed")).count();
                assertEquals((double) passedCases, grade.getEarnedMarks(), "timeout marks must match completed Passed outcomes");
            }
        }
    }
}
