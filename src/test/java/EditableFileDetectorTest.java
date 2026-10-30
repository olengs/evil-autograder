import static org.junit.jupiter.api.Assertions.*;

import config.AssessmentLoader;
import grading.GradingWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import submission.EditableFileDetector;

//every case uses real template & student folders on disk
class EditableFileDetectorTest {
    private static final String COMMENT_SECTION = "/*\n * Name:\n * Email ID:\n */\n";

    @TempDir
    Path temp;
    private Path template;

    @BeforeEach
    void createTemplate() throws IOException {
        template = temp.resolve("RenameToYourUsername");
        write(template.resolve("Q3/Q3.java"), COMMENT_SECTION + "public class Q3 { }");
        write(template.resolve("Q3/ShapeComparator.java"), COMMENT_SECTION + "public class ShapeComparator { }");
        write(template.resolve("Q3/Given.java"), "public class Given { }");
        write(template.resolve("Q3/data.txt"), "template data");
        write(template.resolve("Q3/Shape.class"), "template class");
    }

    @Test
    void templateCommentSectionMarksSupportFileEditable() throws IOException {
        assertEquals(List.of("Q3.java", "ShapeComparator.java"), detect().getEditableFiles());
    }

    @Test
    void filesWithoutCommentSectionStayLocked() throws IOException {
        assertEquals(List.of("Given.java", "Shape.class", "data.txt"), detect().getLockedResourceFiles());
    }

    @Test
    void studentsDeletingTheCommentCannotLockATemplateFile() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/ShapeComparator.java"), "public class ShapeComparator { }");

        assertTrue(detect(student).getEditableFiles().contains("ShapeComparator.java"));
    }

    @Test
    void unionKeepsFileEditableWhenOnlySomeStudentsKeptTheComment() throws IOException {
        write(template.resolve("Q3/Sorter.java"), "public class Sorter { }");
        Path deleted = student("a");
        Path kept = student("b");
        write(deleted.resolve("Q3/Sorter.java"), "public class Sorter { }");
        write(kept.resolve("Q3/Sorter.java"), "// Name: B\npublic class Sorter { }");

        assertTrue(detect(deleted, kept).getEditableFiles().contains("Sorter.java"));
    }

    @Test
    void filledInCommentSectionCounts() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/Given.java"), "// Email ID: a@smu.edu.sg\npublic class Given { }");

        assertTrue(detect(student).getEditableFiles().contains("Given.java"));
    }

    @Test
    void studentsOwnHelperIsNotAddedForTheClass() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/ShapeComparators.java"), COMMENT_SECTION + "public class ShapeComparators { }");

        assertFalse(detect(student).getEditableFiles().contains("ShapeComparators.java"));
    }

    @Test
    void dataFileIsNeverEditableEvenIfItLooksLikeAHeader() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/data.txt"), "Name: not a header");

        assertTrue(detect(student).getLockedResourceFiles().contains("data.txt"));
    }

    @Test
    void commentAfterTheCodeStartsIsNotACommentSection() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/Given.java"), "public class Given {\n// Name: inside the code\n}");

        assertFalse(detect(student).getEditableFiles().contains("Given.java"));
    }

    @Test
    void requiredFileIsEditableWithoutAnyCommentSection() throws IOException {
        write(template.resolve("Q3/Q3.java"), "public class Q3 { }");

        assertTrue(detect().getEditableFiles().contains("Q3.java"));
    }

    @Test
    void submissionThatFailedExtractionIsSkipped() throws IOException {
        assertEquals(List.of("Q3.java", "ShapeComparator.java"), detect(temp.resolve("deleted")).getEditableFiles());
    }

    @Test
    void workspaceRestoresLockedFilesButKeepsStudentWork() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/Q3.java"), "student answer");
        write(student.resolve("Q3/ShapeComparator.java"), "student comparator");
        write(student.resolve("Q3/Given.java"), "accidental edit");
        write(student.resolve("Q3/data.txt"), "accidental edit");
        write(student.resolve("Q3/Shape.class"), "accidental edit");
        write(student.resolve("Q3/Helper.java"), "student helper");
        write(student.resolve("Q3/personstester.txt"), "tester data");

        try (GradingWorkspace workspace = prepare(student)) {
            Path folder = workspace.getDirectory();
            assertEquals("student answer", Files.readString(folder.resolve("Q3.java")));
            assertEquals("student comparator", Files.readString(folder.resolve("ShapeComparator.java")));
            assertEquals("public class Given { }", Files.readString(folder.resolve("Given.java")));
            assertEquals("template data", Files.readString(folder.resolve("data.txt")));
            assertEquals("template class", Files.readString(folder.resolve("Shape.class")));
            assertEquals("student helper", Files.readString(folder.resolve("Helper.java")));
            assertEquals("tester data", Files.readString(folder.resolve("personstester.txt")));
        }
    }

    @Test
    void workspaceFallsBackToTemplateWhenEditableFileIsDeleted() throws IOException {
        Path student = student("a");
        write(student.resolve("Q3/Q3.java"), "student answer");

        try (GradingWorkspace workspace = prepare(student)) {
            assertTrue(Files.readString(workspace.getDirectory().resolve("ShapeComparator.java")).contains("class ShapeComparator"));
        }
    }

    private GradingWorkspace prepare(Path student) throws IOException {
        Path tester = temp.resolve("testers/Q3Tester.java");
        write(tester, "tester");
        GradingWorkspace workspace = new GradingWorkspace();
        QuestionDefinition question = detect(student);
        workspace.prepare(question, new QuestionAttempt(question, student.resolve("Q3"), List.of(), List.of()),
                new TesterDefinition("q3", tester.getParent(), tester.getFileName(), "Q3Tester"));
        return workspace;
    }

    private QuestionDefinition detect(Path... studentRoots) throws IOException {
        List<StudentSubmission> submissions = new ArrayList<>();
        for (Path root : studentRoots) {
            submissions.add(new StudentSubmission(null, root.resolveSibling("student.zip"), root));
        }
        return EditableFileDetector.detect(AssessmentLoader.load(template), submissions).getQuestions().getFirst();
    }

    private Path student(String name) {
        return temp.resolve("students").resolve(name);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
