package app;

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
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionAttempt;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import submission.StudentIdentifier;
import submission.SubmissionLoader;
import validation.AnswerFileLocator;
import validation.SubmissionValidator;
import validation.rules.FileNamingRule;
import validation.rules.QuestionFolderRule;
import validation.rules.RequiredFileRule;
import validation.rules.StudentHeaderRule;
import validation.rules.UnexpectedFileRule;

public class Pair1 {

    public static void run(Path projectFolder) throws IOException {
        Path workspace = Files.createTempDirectory("pair1-");
        run(projectFolder, workspace);
        System.out.println("extraction workspace > " + workspace);
    }

    public static List<StudentSubmission> run(Path projectFolder, Path workspace) throws IOException {
        projectFolder = projectFolder.toAbsolutePath().normalize();
        if (projectFolder.getFileName() != null && projectFolder.getFileName().toString().equals("student-submission")) {
            projectFolder = projectFolder.getParent();
        }
        Path submissionFolder = projectFolder.resolve("student-submission");
        if (!Files.isDirectory(submissionFolder)) {
            throw new IOException("student-submission folder not found in " + projectFolder);
        }
        Path config = Files.isDirectory(projectFolder.resolve("Project")) ? projectFolder.resolve("Project") : projectFolder;
        List<StudentDetails> students = readSampleRoster(config.resolve("IS442-ScoreSheet.csv"));
        String resources = config.resolve("RenameToYourUsername").toString();
        AssessmentDefinition assessment = new AssessmentDefinition("CS102 sample", List.of(
                new QuestionDefinition("Q1a", "Q1", resources, List.of("Q1a.java")),
                new QuestionDefinition("Q1b", "Q1", resources, List.of("Q1b.java")),
                new QuestionDefinition("Q2a", "Q2", resources, List.of("Q2a.java"), List.of(Path.of("students.txt"), Path.of("persons.txt"))),
                new QuestionDefinition("Q2b", "Q2", resources, List.of("Q2b.java"), List.of(Path.of("students.txt"), Path.of("persons.txt"))),
                new QuestionDefinition("Q3", "Q3", resources, List.of("Q3.java"), List.of(Path.of("ShapeComparator.java")))));
        return run(submissionFolder, workspace, students, assessment);
    }

    private static List<StudentDetails> readSampleRoster(Path scoresheet) throws IOException {
        List<StudentDetails> students = new ArrayList<>();
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
            students.add(new StudentDetails(fields[3].strip(), fields[1].strip().replaceFirst("^#", ""), fields[4].strip()));
        }
        return students;
    }

    public static List<StudentSubmission> run(Path submissionFolder, Path workspaceFolder,
                List<StudentDetails> students, AssessmentDefinition assessment) throws IOException {

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
                return name.substring(0, name.length() - 4).equals(folder.getFileName().toString());
            }).findFirst().orElseThrow(() -> new IOException("cannot find original ZIP for " + folder));
            StudentDetails student = identifier.identify(zip, students);
            StudentSubmission submission;
            try {
                if (student == null) {
                    throw new IOException("no roster match for " + zip.getFileName());
                }
                Path answerRoot = folder;
                if (assessment.getQuestions().stream().noneMatch(q -> Files.isDirectory(folder.resolve(q.getExpectedFolder())))) {
                    try (var children = Files.list(folder)) {
                        var roots = children.filter(Files::isDirectory)
                                .filter(child -> !child.getFileName().toString().equals("__MACOSX"))
                                .filter(child -> assessment.getQuestions().stream().anyMatch(q ->
                                        Files.isDirectory(child.resolve(q.getExpectedFolder())))).toList();
                        if (roots.size() == 1) answerRoot = roots.get(0);
                    }
                }
                submission = new StudentSubmission(student, zip, answerRoot);
                validator.validate(submission, assessment);
            } catch (IOException error) {
                submission = new StudentSubmission(student, zip, folder);
                SubmissionIssue issue = new SubmissionIssue("submission preparation error", IssueSeverity.ERROR,
                        error.getMessage(), null, folder);
                submission.addIssues(List.of(issue));
                for (QuestionDefinition question : assessment.getQuestions()) {
                    submission.addQuestionAttempt(new QuestionAttempt(question, List.of(), List.of(issue)));
                }
            }
            submissions.add(submission);
            printSubmission(submission);
        }
        long ready = submissions.stream().filter(submission -> submission.getQuestionAttempts().stream()
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
