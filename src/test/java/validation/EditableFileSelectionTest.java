// Tests trusted-template file selection and the submitted-file handoff to grading.
// Testing only; not required by the application.

package validation;

import static org.junit.jupiter.api.Assertions.*;
import static support.SubmissionFixtures.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import model.AssessmentDefinition;
import model.QuestionAttempt;
import model.QuestionDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.SubmissionFileFinder;

class EditableFileSelectionTest {
    @TempDir Path directory;

    private QuestionAttempt validate(List<String> required, List<String> allowed) {
        QuestionDefinition question = question(directory.resolve("templates"), "q1", "Q1", required, allowed);
        var validator = new SubmissionValidator(List.of(), List.of(), new SubmissionFileFinder());
        var result = validator.validate(submission(directory.resolve("student")),
                new AssessmentDefinition("exam", List.of(question)));
        return result.getQuestionAttempts().getFirst();
    }

    @Test
    void requiredAnswerIsSelectedWithoutStudentOrTemplateHeader() throws IOException {
        Path answer = write(directory, "student/Q1/Answer.java", "class Answer {}\n");
        var attempt = validate(List.of("Answer.java"), List.of());
        assertEquals(List.of(answer), attempt.getSubmittedFiles());
        assertEquals(directory.resolve("student/Q1").toAbsolutePath().normalize(), attempt.getQuestionDirectory());
    }

    @Test
    void optionalJavaFileNeedsBothTrustedTemplateLabels() throws IOException {
        write(directory, "templates/Q1/Editable.java", "// Name:\n// Email:\nclass Editable {}\n");
        write(directory, "templates/Q1/NameOnly.java", "// Name:\nclass NameOnly {}\n");
        Path editable = write(directory, "student/Q1/Editable.java", "class Editable {}\n");
        write(directory, "student/Q1/NameOnly.java", HEADER + "class NameOnly {}\n");
        assertEquals(List.of(editable), validate(List.of(), List.of("Editable.java", "NameOnly.java")).getSubmittedFiles());
    }

    @Test
    void studentHeadersCannotPromoteProtectedOrUndeclaredFiles() throws IOException {
        write(directory, "templates/Q1/Helper.java", "class Helper {}\n");
        write(directory, "student/Q1/Helper.java", HEADER + "class Helper {}\n");
        write(directory, "student/Q1/Injected.java", HEADER + "class Injected {}\n");
        assertTrue(validate(List.of(), List.of("Helper.java")).getSubmittedFiles().isEmpty());
    }

    @Test
    void absentOptionalAnswerDoesNotBecomeAMissingFileError() throws IOException {
        write(directory, "templates/Q1/Editable.java", "// Name:\n// Email:\n");
        var attempt = validate(List.of(), List.of("Editable.java"));
        assertTrue(attempt.getSubmittedFiles().isEmpty());
        assertTrue(attempt.getIssues().isEmpty());
    }

    @Test
    void nonJavaResourcesAreNotCopiedAsEditableAnswers() throws IOException {
        write(directory, "student/Q1/input.txt", HEADER);
        assertTrue(validate(List.of(), List.of("input.txt")).getSubmittedFiles().isEmpty());
    }

    @Test
    void missingTrustedJavaTemplateRecordsQuestionReadError() throws IOException {
        write(directory, "student/Q1/Answer.java", "answer");
        var attempt = validate(List.of("Answer.java"), List.of("Missing.java"));
        assertTrue(attempt.getSubmittedFiles().isEmpty());
        assertFalse(attempt.isValid());
        assertEquals("file read error", attempt.getIssues().getFirst().getCode());
    }
}
