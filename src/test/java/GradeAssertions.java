import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import model.*;

class GradeAssertions { //checks one question grade according to its status
    static void check(StudentSubmission submission, QuestionGrade grade) {
        assertTrue(grade.getEarnedMarks() >= 0);
        var attempt = submission.getQuestionAttempts().stream()
                .filter(value -> value.getQuestionDefinition().getId().equals(grade.getQuestionId()))
                .findFirst().orElseThrow();
        boolean blocked = !attempt.isValid();
        for (var issue : submission.getSubmissionIssues()) {
            if (issue.getSeverity() == IssueSeverity.ERROR
                    && (issue.getQuestionId() == null || issue.getQuestionId().equals(grade.getQuestionId()))) {
                blocked = true;
            }
        }
        if (blocked)
            assertEquals(GradeStatus.SKIPPED, grade.getStatus());
        switch (grade.getStatus()) {
            case SKIPPED -> checkSkipped(grade, blocked);
            case COMPLETED -> checkCompleted(grade);
            default -> checkFailed(grade);
        }
    }

    private static void checkSkipped(QuestionGrade grade, boolean blocked) {
        assertTrue(blocked || grade.getMessage().equals("missing tester"), grade.getMessage());
        assertEquals(0, grade.getEarnedMarks());
        assertTrue(grade.getProcessResult().isEmpty());
        assertFalse(grade.getMessage().isBlank());
    }

    private static void checkCompleted(QuestionGrade grade) {
        var process = grade.getProcessResult().orElseThrow();
        assertTrue(process.isSuccessful());
        assertFalse(grade.getTestCaseResults().isEmpty());
        checkMarks(grade);
        //@1ejun6 > compare parsed marks with total printed by the actual tester
        String printedTotal = process.getStandardOutput().strip().lines().toList().getLast();
        assertEquals(Double.parseDouble(printedTotal), grade.getEarnedMarks());
    }

    private static void checkFailed(QuestionGrade grade) {
        assertTrue(List.of(GradeStatus.COMPILE_ERROR, GradeStatus.RUNTIME_ERROR, GradeStatus.TIMEOUT)
                .contains(grade.getStatus()));
        var process = grade.getProcessResult().orElseThrow();
        assertFalse(process.isSuccessful());
        assertEquals(grade.getStatus() == GradeStatus.TIMEOUT, process.isTimedOut());
        checkMarks(grade);
        if (process.isTimedOut()) {
            long passedCases = process.getStandardOutput().lines()
                    .filter(line -> line.strip().equalsIgnoreCase("Passed")).count();
            assertEquals(passedCases, grade.getEarnedMarks());
        } else {
            assertEquals(0, grade.getEarnedMarks());
        }
    }

    private static void checkMarks(QuestionGrade grade) {
        double total = 0;
        for (var testCase : grade.getTestCaseResults())
            total += testCase.getEarnedMarks();
        assertEquals(total, grade.getEarnedMarks());
    }
}
