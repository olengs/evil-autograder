package submission;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;

import validation.SubmissionIssue;

public class StudentSubmission { //groups a student archive, extracted root, attempts & validation findings
    private final StudentDetails student;
    private final Path originalZip;
    private final Path rootDirectory;
    private final List<QuestionAttempt> questionAttempts;
    private final List<SubmissionIssue> submissionIssues;
    private final List<String> possibleIdentifiers;
    private final boolean conflictingIdentifiers;

    public StudentSubmission(StudentDetails student, Path originalZip, Path rootDirectory) {
        this(student, originalZip, rootDirectory, List.of(originalZip.getFileName().toString()), false);
    }

    public StudentSubmission(StudentDetails student, Path originalZip, Path rootDirectory,
            List<String> possibleIdentifiers, boolean conflictingIdentifiers) {
        this.student = student;
        this.originalZip = originalZip;
        this.rootDirectory = rootDirectory;
        this.questionAttempts = new ArrayList<>();
        this.submissionIssues = new ArrayList<>();
        var identifiers = new LinkedHashSet<String>();
        for (String identifier : possibleIdentifiers) {
            if (identifier != null && !identifier.isBlank()) identifiers.add(identifier);
        }
        this.possibleIdentifiers = List.copyOf(identifiers);
        this.conflictingIdentifiers = conflictingIdentifiers;
    }

    public List<String> getPossibleIdentifiers() {
        return possibleIdentifiers;
    }

    public String getIdentificationFlag() {
        if (student != null) return "";
        return conflictingIdentifiers ? "CONFLICTING_IDENTIFIERS" : "NO_USERNAME";
    }

    public String getIdentificationMessage() {
        if (student != null) return "";
        String reason = conflictingIdentifiers ? "Conflicting header usernames found" : "No username found";
        return reason + ", possible identifiers: " + String.join(", ", possibleIdentifiers);
    }

    public StudentDetails getStudent() {
        return student;
    }

    public String getDisplayName() { //uses archive filename when no student matched
        return student == null ? originalZip.getFileName().toString() : student.getUsername();
    }

    public Path getOriginalZip() {
        return originalZip;
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public List<QuestionAttempt> getQuestionAttempts() {
        return List.copyOf(questionAttempts);
    }

    public Optional<QuestionAttempt> findAttempt(String questionId) {
        for (QuestionAttempt attempt : questionAttempts) {
            if (attempt.getQuestionDefinition().getId().equals(questionId)) return Optional.of(attempt);
        }
        return Optional.empty();
    }

    public List<SubmissionIssue> getSubmissionIssues() {
        return List.copyOf(submissionIssues);
    }

    public void addQuestionAttempt(QuestionAttempt attempt) {
        questionAttempts.add(attempt);
    }

    public void addIssues(List<SubmissionIssue> issues) {
        submissionIssues.addAll(issues);
    }
}
