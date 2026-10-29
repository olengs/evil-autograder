import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import model.*;

class GradeAssertions { //@1ejun6 > checks one question grade according to its status
    static void check(StudentSubmission submission, QuestionGrade grade) {
        assertTrue(grade.getEarnedMarks() >= 0);

        //@1ejun6 > 1 > find this question grade's matching validated attempt
        QuestionAttempt attempt = null;
        for (QuestionAttempt candidate : submission.getQuestionAttempts()) {
            if (candidate.getQuestionDefinition().getId().equals(grade.getQuestionId())) {
                attempt = candidate;
                break;
            }
        }

        assertNotNull(attempt, "grade must have a matching question attempt");

        //@1ejun6 > 2 > check whether validation should have blocked grading
        //@1ejun6 > include question errors & errors affecting this whole submission
        boolean blocked = !attempt.isValid();
        for (var issue : submission.getSubmissionIssues()) {
            boolean iserror = issue.getSeverity() == IssueSeverity.ERROR;
            boolean affectsquestion = issue.getQuestionId() == null || issue.getQuestionId().equals(grade.getQuestionId());

            if (iserror && affectsquestion) {
                blocked = true;
            }
        }
        if (blocked) {
            assertEquals(GradeStatus.SKIPPED, grade.getStatus());
        }

        //@1ejun6 > 3 > check expected behaviour for each grading status
        switch (grade.getStatus()) {
            case SKIPPED -> checkSkipped(grade, blocked);
            case COMPLETED -> checkCompleted(grade);
            default -> checkFailed(grade);
        }
    }

    //@1ejun6 > a skipped question needs a reason & zero marks & no process result
    private static void checkSkipped(QuestionGrade grade, boolean blocked) {
        assertTrue(blocked || grade.getMessage().equals("missing tester"), grade.getMessage());
        assertEquals(0, grade.getEarnedMarks());
        assertTrue(grade.getProcessResult().isEmpty());
        assertFalse(grade.getMessage().isBlank());
    }

    //@1ejun6 > a completed question needs a successful process & parsed test cases
    //@1ejun6 > earned marks must match case totals & tester output
    private static void checkCompleted(QuestionGrade grade) {
        var process = grade.getProcessResult().orElseThrow();
        assertTrue(process.isSuccessful());
        assertFalse(grade.getTestCaseResults().isEmpty());
        checkMarks(grade);

        //@1ejun6 > compare parsed marks with total printed by our tester
        String printedTotal = process.getStandardOutput().strip().lines().toList().getLast();
        assertEquals(Double.parseDouble(printedTotal), grade.getEarnedMarks());
    }

    //@1ejun6 > compilation failures & runtime failures earn zero > timeouts may retain completed test marks
    private static void checkFailed(QuestionGrade grade) {
        assertTrue(List.of(GradeStatus.COMPILE_ERROR, GradeStatus.RUNTIME_ERROR, GradeStatus.TIMEOUT).contains(grade.getStatus()));

        var process = grade.getProcessResult().orElseThrow();
        assertFalse(process.isSuccessful());
        assertEquals(grade.getStatus() == GradeStatus.TIMEOUT, process.isTimedOut());
        checkMarks(grade);

        //@1ejun6 > each printed Passed outcome earns one mark > retain these marks after a timeout
        if (process.isTimedOut()) {
            long passedCases = 0;
            for (String line : process.getStandardOutput().lines().toList()) {
                if (line.strip().equalsIgnoreCase("Passed")) {
                    passedCases++;
                }
            }
            assertEquals(passedCases, grade.getEarnedMarks());
        } else {
            assertEquals(0, grade.getEarnedMarks());
        }
    }

    //@1ejun6 > check that total marks equal marks recorded across test cases
    private static void checkMarks(QuestionGrade grade) {
        double total = 0;
        for (var testCase : grade.getTestCaseResults()) {
            total += testCase.getEarnedMarks();
        }
        assertEquals(total, grade.getEarnedMarks());
    }
}
