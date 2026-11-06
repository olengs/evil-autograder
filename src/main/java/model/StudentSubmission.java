// Tracks one student's archive, working root, and results.
// Collects preparation issues, validation findings, and question attempts.

package model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentSubmission { //stores a students zip & extracted folder & validation results together
    private final StudentDetails student;
    private final Path originalZip;
    private final Path rootDirectory;
    private final List<QuestionAttempt> questionAttempts;
    private final List<SubmissionIssue> submissionIssues;

    public StudentSubmission(StudentDetails student, Path originalZip, Path rootDirectory) {
        this.student = student;
        this.originalZip = originalZip;
        this.rootDirectory = rootDirectory;
        this.questionAttempts = new ArrayList<>();
        this.submissionIssues = new ArrayList<>();
    }

    public StudentDetails getStudent() {
        return student;
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
            if (attempt.getQuestionDefinition().getId().equals(questionId)) {
                return Optional.of(attempt);
            }
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
