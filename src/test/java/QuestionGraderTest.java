import static org.junit.jupiter.api.Assertions.*;

import app.Main;
import config.AssessmentLoader;
import config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import submission.SubmissionLoader;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuestionGraderTest { //runs integration tests using the real project submissions
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private AssessmentDefinition assessment;
    private long zipCount;

    @BeforeAll
    void gradeProject() throws Exception {
        Config config = new Config();
        assessment = AssessmentLoader.load(config.getResourcesDirectory());
        zipCount = SubmissionLoader.findZipFiles(config.getSubmissionsDirectory()).size();
        results = Main.run(config);
        Main.printResults(results);
    }

    @Test
    void everyZipAndQuestionHasAResult() {
        assertTrue(zipCount > 0, "project must contain submission ZIPs");
        assertEquals(zipCount, results.size());
        var questionIds = assessment.getQuestions().stream().map(QuestionDefinition::getId).toList();
        boolean hasPassingGrade = false;
        for (var grades : results.values()) {
            assertEquals(questionIds, grades.stream().map(QuestionGrade::getQuestionId).toList());
            for (var grade : grades) {
                if (grade.getStatus() == GradeStatus.COMPLETED && grade.getEarnedMarks() > 0) hasPassingGrade = true;
            }
        }
        assertTrue(hasPassingGrade, "grader must actually compile & run answers > not skip everything");
    }

    @TestFactory
    List<DynamicTest> checkEachStudentQuestion() {
        List<DynamicTest> tests = new ArrayList<>();
        for (var entry : results.entrySet()) {
            StudentSubmission submission = entry.getKey();
            for (QuestionGrade grade : entry.getValue()) {
                String name = submission.getOriginalZip().getFileName() + " " + grade.getQuestionId();
                tests.add(DynamicTest.dynamicTest(name, () -> GradeAssertions.check(submission, grade)));
            }
        }
        return tests;
    }
}
