package grading.verification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import app.Autograder;
import assessment.AssessmentDefinition;
import assessment.AssessmentLoader;
import assessment.QuestionDefinition;
import assessment.TesterDefinition;
import assessment.TesterLoader;
import config.Config;
import grading.QuestionGrade;
import grading.progress.GradingProgress;
import support.GradingFixture;
import utils.logging.JsonLineLogger;
import org.junit.jupiter.api.AfterAll;
import submission.StudentSubmission;
import submission.SubmissionArchives;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class AutograderTest { //grades once & checks result coverage & consistency independently of student scores
    private Map<StudentSubmission, List<QuestionGrade>> results;
    private AssessmentDefinition assessment;
    private Map<String, TesterDefinition> testers;
    private Set<Path> archives;

    @TempDir
    static Path directory;

    @BeforeAll
    void gradeProject() throws Exception {
        Config config = GradingFixture.create(directory);
        JsonLineLogger.getInstance().Init(directory.resolve("test.log"));
        assessment = AssessmentLoader.load(config.getResourcesDirectory());
        testers = TesterLoader.load(config.getTestersDirectory());
        
        archives = new HashSet<>();
        List<Path> zipFiles = SubmissionArchives.listZipFiles(config.getSubmissionsDirectory());
        for (Path zip : zipFiles) archives.add(zip.toAbsolutePath().normalize());
        results = Autograder.run(config, new GradingProgress());
    }

    @AfterAll
    void flushLogs() throws Exception {
        JsonLineLogger.getInstance().flush();
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

        List<String> questionIds = assessment.getQuestions().stream().map(QuestionDefinition::getId).toList();
        for (var grades : results.values()) {
            List<String> gradedQuestionIds = grades.stream().map(QuestionGrade::getQuestionId).toList();
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
                tests.add(DynamicTest.dynamicTest(name,() -> GradeAssertions.check(submission, grade, hasTester)));
            }
        }

        return tests;
    }
}
