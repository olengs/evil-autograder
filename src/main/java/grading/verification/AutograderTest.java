package grading.verification;

import app.Autograder;
import assessment.AssessmentDefinition;
import assessment.AssessmentLoader;
import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import assessment.TesterLoader;
import config.Config;
import grading.QuestionGrade;
import grading.GradeStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.junit.jupiter.api.Assertions.*;
import report.ConsoleReportPrinter;
import submission.StudentSubmission;
import submission.SubmissionArchives;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(GradingSession.class)
@Execution(ExecutionMode.CONCURRENT)
class AutograderTest { //grades once & checks result coverage, consistency & a positive score
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private AssessmentDefinition assessment;
    private Map<String, TesterDefinition> testers;
    private Set<Path> archives;

    @BeforeAll
    void gradeProject(GradingSession.Input run) throws Exception {
        Config config = run.config();
        assessment = AssessmentLoader.load(config.getResourcesDirectory());
        testers = TesterLoader.load(config.getTestersDirectory());
        
        archives = new HashSet<>();
        List<Path> zipFiles = SubmissionArchives.listZipFiles(config.getSubmissionsDirectory());
        for (int i = 0; i < zipFiles.size(); i++) archives.add(zipFiles.get(i).toAbsolutePath().normalize());
        if (run.results() == null) {
            results = Autograder.run(config, run.progress());
            run.complete(results);
            if (run.printResults()) ConsoleReportPrinter.print(results);
        } else results = run.results();
    }

    @Test
    void everyZipAndQuestionHasAResult() {
        assertFalse(archives.isEmpty(), "assessment must contain submission ZIPs");
        assertEquals(archives.size(), results.size(), "every ZIP must have one result entry");
        Set<Path> gradedArchives = new HashSet<>();
        for (var submission : results.keySet()) {
            gradedArchives.add(submission.getOriginalZip().toAbsolutePath().normalize());
        }
        assertEquals(archives, gradedArchives);

        List<String> questionIds = new ArrayList<>();
        for (QuestionDefinition question : assessment.getQuestions()) {
            questionIds.add(question.getId());
        }
        boolean hasPassingGrade = false;
        for (var grades : results.values()) {
            List<String> gradedQuestionIds = new ArrayList<>();
            for (QuestionGrade grade : grades) {
                gradedQuestionIds.add(grade.getQuestionId());
            }
            assertEquals(questionIds, gradedQuestionIds);
            for (QuestionGrade grade : grades) {
                if (grade.getStatus() == GradeStatus.completed && grade.getEarnedMarks() > 0) hasPassingGrade = true;
            }
        }
        assertTrue(hasPassingGrade, "grader must actually compile & run answers > not skip everything");
    }

    @TestFactory
    List<DynamicTest> checkEachStudentQuestion() {
        List<DynamicTest> tests = new ArrayList<>();

        for (var entry : results.entrySet()) {
            var submission = entry.getKey();

            for (var grade : entry.getValue()) {
                String name = submission.getOriginalZip().getFileName() + " " + grade.getQuestionId();
                boolean hasTester = testers.containsKey(grade.getQuestionId().toLowerCase(Locale.ROOT));
                tests.add(DynamicTest.dynamicTest(name,() -> GradeAssertions.check(submission, grade, hasTester)));
            }
        }

        return tests;
    }
}
