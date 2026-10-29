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

//@1ejun6 > reuse one test instance > all checks share results from one grading run
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuestionGraderTest { //@1ejun6 > runs integration tests using real project submissions
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private AssessmentDefinition assessment;
    private long zipCount;

    //@1ejun6 > 1 > run integration once before checking its results
    //@1ejun6 > config reads project location > tests need actual project input files
    @BeforeAll
    void gradeProject() throws Exception {
        Config config = new Config();
        assessment = AssessmentLoader.load(config.getResourcesDirectory());
        zipCount = SubmissionLoader.findZipFiles(config.getSubmissionsDirectory()).size();

        //@1ejun6 > run extraction & validation & compilation & testing together
        results = Main.run(config);
        Main.printResults(results);
    }

    //@1ejun6 > 2 > check that every submission zip has a result for every question
    @Test
    void everyZipAndQuestionHasAResult() {
        assertTrue(zipCount > 0, "project must contain submission ZIPs");
        assertEquals(zipCount, results.size());

        List<String> questionids = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            questionids.add(question.getId());
        }
        boolean hasPassingGrade = false;

        for (var grades : results.values()) {
            List<String> gradedquestionids = new ArrayList<>();

            for (QuestionGrade grade : grades) {
                gradedquestionids.add(grade.getQuestionId());
            }
            assertEquals(questionids, gradedquestionids);
            for (var grade : grades) {
                if (grade.getStatus() == GradeStatus.COMPLETED && grade.getEarnedMarks() > 0) {
                    hasPassingGrade = true;
                }
            }
        }

        //@1ejun6 > require a completed answer with marks > skipping every answer must fail this check
        assertTrue(hasPassingGrade, "grader must actually compile & run answers > not skip everything");
    }

    //@1ejun6 > 3 > create a named JUnit check for each student question result
    //@1ejun6 > each test name identifies its submission zip & question
    @TestFactory
    List<DynamicTest> checkEachStudentQuestion() {
        List<DynamicTest> tests = new ArrayList<>();
        for (var entry : results.entrySet()) {
            StudentSubmission submission = entry.getKey();

            for (QuestionGrade grade : entry.getValue()) {
                String name = submission.getOriginalZip().getFileName() + " " + grade.getQuestionId();
                //@1ejun6 > JUnit runs this lambda to check a grade using shared assertions
                tests.add(DynamicTest.dynamicTest(name, () -> GradeAssertions.check(submission, grade)));
            }
        }
        return tests;
    }
}
