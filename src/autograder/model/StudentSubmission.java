// Tracks one student archive, working root, and results.
// Collects validation issues and per-question attempts.

package autograder.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StudentSubmission {
    private Student student;
    private Path originalZip;
    private Path rootDirectory;
    private List<QuestionAttempt> questionAttempts;
    private List<SubmissionIssue> submissionIssues;

    public StudentSubmission(Student student, Path originalZip, Path rootDirectory) {
        this.student = student;
        this.originalZip = originalZip;
        this.rootDirectory = rootDirectory;
        this.questionAttempts = new ArrayList<>();
        this.submissionIssues = new ArrayList<>();
    }

    public Student getStudent() {
        return student;
    }

    public Path getOriginalZip() {
        return originalZip;
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public List<QuestionAttempt> getQuestionAttempts() {
        return questionAttempts;
    }

    public List<SubmissionIssue> getSubmissionIssues() {
        return submissionIssues;
    }

    public void addQuestionAttempt(QuestionAttempt attempt) {
        questionAttempts.add(attempt);
    }

    public void addIssues(List<SubmissionIssue> issues) {
        submissionIssues.addAll(issues);
    }
}
