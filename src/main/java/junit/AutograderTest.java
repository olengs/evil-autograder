package junit;

import static org.junit.jupiter.api.Assertions.*;
import app.Main;
import config.AssessmentLoader;
import config.Config;
import grading.TesterLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import model.AssessmentDefinition;
import model.QuestionDefinition;
import model.QuestionGrade;
import model.StudentSubmission;
import model.TesterDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import submission.SubmissionLoader;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) //checks selected assessments results without assuming particular student scores

class AutograderTest {
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private AssessmentDefinition assessment;
    private Map<String, TesterDefinition> testers;
    private Set<Path> archives;

    @BeforeAll
    void gradeProject() throws Exception {
        var run = JUnitRunner.currentRun();
        var config = run == null ? new Config(Path.of(System.getProperty("autograder.config", "config.properties"))) : run.config();
        assessment = AssessmentLoader.load(config.getResourcesDirectory());
        testers = TesterLoader.load(config.getTestersDirectory());
        archives = new HashSet<>();
        for (Path archive : SubmissionLoader.findZipFiles(config.getSubmissionsDirectory())) {
            archives.add(archive.toAbsolutePath().normalize());
        }
        results = run == null ? Main.run(config) : run.results();
        if (run == null)
            Main.printResults(results);
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
        for (var grades : results.values()) {
            List<String> gradedQuestionIds = new ArrayList<>();
            for (QuestionGrade grade : grades) {
                gradedQuestionIds.add(grade.getQuestionId());
            }
            assertEquals(questionIds, gradedQuestionIds);
        }
    }

    @TestFactory
    List<DynamicTest> checkEachStudentQuestion() {
        List<DynamicTest> tests = new ArrayList<>();

        for (var entry : results.entrySet()) {
            var submission = entry.getKey();

            for (var grade : entry.getValue()) {
                String name = submission.getOriginalZip().getFileName() + " " + grade.getQuestionId();
                boolean hasTester = testers.containsKey(grade.getQuestionId().toLowerCase(Locale.ROOT));
                tests.add(DynamicTest.dynamicTest(name,
                        () -> GradeAssertions.check(submission, grade, hasTester)));
            }
        }

        return tests;
    }
}
