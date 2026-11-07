package verification;

import static org.junit.jupiter.api.Assertions.*;

import app.GradingRun;
import config.AssessmentLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import submission.SubmissionLoader;

//@1ejun6 > !update
//@1ejun6 > reuse one test instance > all checks share results from one grading run
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(GradingSession.class)
@Execution(ExecutionMode.CONCURRENT)
public class GradingChecks { //same JUnit suite used by the TUI and Maven
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private AssessmentDefinition assessment;
    private long zipCount;

    //@1ejun6 > 1 > run integration once before checking its results
    //@1ejun6 > config reads project location > tests need actual project input files
    @BeforeAll
    protected void gradeProject(GradingSession.Input input) throws Exception {
        var config = input.config();
        assessment = AssessmentLoader.load(config.getResourcesDirectory());
        zipCount = SubmissionLoader.findZipFiles(config.getSubmissionsDirectory()).size();

        //@1ejun6 > run extraction & validation & compilation & testing together
        results = GradingRun.run(config, input.progress());
        if (input.printResults()) GradingRun.printResults(results);
    }

    //@1ejun6 > 2 > check that every submission zip has a result for every question
    @Test
    protected void everyZipAndQuestionHasAResult() {
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
                if (grade.getStatus() == GradeStatus.completed && grade.getEarnedMarks() > 0) {
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
    protected List<DynamicTest> checkEachStudentQuestion() {
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
