package autograder.app;

import autograder.model.AssessmentDefinition;
import autograder.model.IssueSeverity;
import autograder.model.QuestionAttempt;
import autograder.model.QuestionDefinition;
import autograder.model.Student;
import autograder.model.StudentSubmission;
import autograder.model.SubmissionIssue;
import autograder.submission.StudentIdentifier;
import autograder.submission.SubmissionLoader;
import autograder.submission.SubmissionNormalizer;
import autograder.validation.AnswerFileLocator;
import autograder.validation.SubmissionValidator;
import autograder.validation.rules.FileNamingRule;
import autograder.validation.rules.QuestionFolderRule;
import autograder.validation.rules.RequiredFileRule;
import autograder.validation.rules.StudentHeaderRule;
import autograder.validation.rules.UnexpectedFileRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Pair1 {

    public static void run(Path projectFolder) throws IOException {
        projectFolder = projectFolder.toAbsolutePath().normalize();
        if (projectFolder.getFileName() != null && projectFolder.getFileName().toString().equals("student-submission")) {
            projectFolder = projectFolder.getParent();
        }
        Path submissionFolder = projectFolder.resolve("student-submission");
        if (!Files.isDirectory(submissionFolder)) {
            throw new IOException("student-submission folder not found in " + projectFolder);
        }
        //Sample-project configuration only; pair-3 supplies the real roster and assessment later.
        List<Student> students = readSampleRoster(projectFolder.resolve("IS442-ScoreSheet.csv"));
        AssessmentDefinition assessment = new AssessmentDefinition("CS102 sample", List.of(
                new QuestionDefinition("Q1a", "Q1", List.of("Q1a.java")),
                new QuestionDefinition("Q1b", "Q1", List.of("Q1b.java")),
                new QuestionDefinition("Q2a", "Q2", List.of("Q2a.java"), List.of("students.txt", "persons.txt")),
                new QuestionDefinition("Q2b", "Q2", List.of("Q2b.java"), List.of("students.txt", "persons.txt")),
                new QuestionDefinition("Q3", "Q3", List.of("Q3.java"), List.of("ShapeComparator.java"))));
        Path workspace = Files.createTempDirectory("pair1-");
        run(submissionFolder, workspace, students, assessment);
        System.out.println("extraction workspace > " + workspace);
    }

    private static List<Student> readSampleRoster(Path scoresheet) throws IOException {
        List<Student> students = new ArrayList<>();
        List<String> rows = Files.readAllLines(scoresheet);
        //The supplied scoresheet has unquoted fields: ID, username, last name, first name, email.
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).isBlank()) {
                continue;
            }
            String[] fields = rows.get(i).split(",", -1);
            if (fields.length < 5 || fields[1].isBlank()) {
                throw new IOException("invalid scoresheet row > " + (i + 1));
            }
            String fullName = (fields[3].strip() + " " + fields[2].strip()).strip();
            students.add(new Student(fullName, fields[1].strip().replaceFirst("^#", ""), fields[4].strip()));
        }
        return students;
    }

    public static List<StudentSubmission> run(Path submissionFolder, Path workspaceFolder,
                List<Student> students, AssessmentDefinition assessment) throws IOException {

        List<Path> zipFiles = SubmissionLoader.findZipFiles(submissionFolder);
        StudentIdentifier identifier = new StudentIdentifier();
        AnswerFileLocator locator = new AnswerFileLocator();
        SubmissionValidator validator = new SubmissionValidator(List.of(new QuestionFolderRule(),
                new RequiredFileRule(locator), new FileNamingRule(locator), new StudentHeaderRule(locator)),
                List.of(new UnexpectedFileRule()), locator);
        List<StudentSubmission> submissions = new ArrayList<>();
        System.out.println("\nchecking " + zipFiles.size() + " submissions");
        System.out.println("ready = passed file validation; answers have not been graded");

        for (Path folder : SubmissionLoader.loadSubmissions(submissionFolder, workspaceFolder)) {
            Path zip = zipFiles.stream().filter(path -> {
                String name = path.getFileName().toString();
                return name.substring(0, name.length() - 4).equalsIgnoreCase(folder.getFileName().toString());
            }).findFirst().orElseThrow(() -> new IOException("cannot find original ZIP for " + folder));
            Student student = null;
            StudentSubmission submission;
            try {
                student = identifier.identify(zip, students);
                if (student == null) {
                    throw new IOException("no roster match for " + zip.getFileName());
                }
                submission = new StudentSubmission(student, zip, folder);
                SubmissionNormalizer.normalize(submission, assessment);
                validator.validate(submission, assessment);
            } catch (IOException | RuntimeException error) {
                submission = new StudentSubmission(student, zip, folder);
                SubmissionIssue issue = new SubmissionIssue("submission preparation error", IssueSeverity.ERROR,
                        error.toString(), null, folder);
                submission.addIssues(List.of(issue));
                for (QuestionDefinition question : assessment.getQuestions()) {
                    submission.addQuestionAttempt(new QuestionAttempt(question, List.of(), List.of(issue)));
                }
            }
            submissions.add(submission);
            printSubmission(submission);
        }
        long ready = submissions.stream().filter(submission -> submission.getSubmissionIssues().stream()
                .noneMatch(issue -> issue.getSeverity() == IssueSeverity.ERROR))
                .filter(submission -> submission.getQuestionAttempts().stream()
                .allMatch(QuestionAttempt::isValid)).count();
        System.out.println("\nsummary > " + ready + " ready, " + (submissions.size() - ready) + " invalid, "
                + (zipFiles.size() - submissions.size()) + " could not be extracted");
        return submissions;
    }

    private static void printSubmission(StudentSubmission submission) {
        String name = submission.getStudent() == null ? submission.getOriginalZip().getFileName().toString()
                : submission.getStudent().getUserName();
        System.out.println("\n" + name);
        System.out.println("  " + submission.getQuestionAttempts().stream()
                .map(attempt -> attempt.getQuestionDefinition().getId() + ": "
                        + (attempt.isValid() ? "ready" : "invalid"))
                .collect(Collectors.joining(" | ")));

        Map<String, Set<String>> groups = new LinkedHashMap<>();
        for (SubmissionIssue issue : submission.getSubmissionIssues()) {
            String label = issue.getSeverity().toString().toLowerCase() + " > " + issue.getCode();
            String detail;
            if (issue.getAffectedPath() != null && Set.of("missing question folder", "missing student header",
                    "unexpected file").contains(issue.getCode())) {
                detail = submission.getRootDirectory().relativize(issue.getAffectedPath()).toString();
                if (issue.getCode().equals("unexpected file")) {
                    label += " in " + issue.getAffectedPath().getParent().getFileName();
                    detail = issue.getAffectedPath().getFileName().toString();
                }
            } else {
                detail = issue.getMessage();
            }
            groups.computeIfAbsent(label, key -> new LinkedHashSet<>()).add(detail);
        }
        groups.forEach((label, details) -> {
            String line = "  " + label + ": ";
            for (String detail : details) {
                if (line.length() + detail.length() > 100) {
                    System.out.println(line);
                    line = "    ";
                }
                if (!line.endsWith(": ") && !line.equals("    ")) {
                    line += ", ";
                }
                line += detail;
            }
            System.out.println(line);
        });
    }
}
