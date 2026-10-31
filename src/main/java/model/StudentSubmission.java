package model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public List<SubmissionIssue> getSubmissionIssues() {
        return List.copyOf(submissionIssues);
    }

    //questions sharing a folder each keep their own record > reports show that finding once
    public List<SubmissionIssue> getDistinctIssues() {
        Map<String, SubmissionIssue> distinct = new LinkedHashMap<>();
        for (SubmissionIssue issue : submissionIssues) {
            distinct.putIfAbsent(issue.getCode() + "|" + issue.getMessage() + "|" + issue.getAffectedPath(), issue);
        }
        return List.copyOf(distinct.values());
    }

    public void addQuestionAttempt(QuestionAttempt attempt) {
        questionAttempts.add(attempt);
    }

    public void addIssues(List<SubmissionIssue> issues) {
        submissionIssues.addAll(issues);
    }
}