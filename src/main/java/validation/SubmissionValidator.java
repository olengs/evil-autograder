package validation;

import assessment.AssessmentDefinition;
import assessment.QuestionDefinition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import submission.QuestionAttempt;
import submission.StudentSubmission;
import submission.files.StudentHeaderReader;
import submission.files.SubmissionFileFinder;

public class SubmissionValidator { //runs validation rules & builds question attempts for grading
    private final List<QuestionValidationRule> questionRules;
    private final List<SubmissionValidationRule> submissionRules;
    private final SubmissionFileFinder fileFinder;
    private final Map<QuestionDefinition, TemplateSelection> templateSelections = new HashMap<>();

    private record TemplateSelection(List<String> fileNames, IOException error) {}

    public SubmissionValidator(List<QuestionValidationRule> questionRules, List<SubmissionValidationRule> submissionRules, SubmissionFileFinder fileFinder) {
        this.questionRules = List.copyOf(questionRules);
        this.submissionRules = List.copyOf(submissionRules);
        this.fileFinder = fileFinder;
    }

    public void validate(StudentSubmission submission, AssessmentDefinition assessment) {
        if (!submission.getQuestionAttempts().isEmpty()) throw new IllegalStateException("submission already has question attempts");

        ValidationFiles files = new ValidationFiles(submission, fileFinder);
        for (SubmissionValidationRule rule : submissionRules) {
            List<SubmissionIssue> issues = rule.validate(submission, assessment, files);
            submission.addIssues(issues);
        }

        for (QuestionDefinition question : assessment.getQuestions()) {
            validateQuestion(submission, question, files.question(question));
        }
    }

    private void validateQuestion(StudentSubmission submission, QuestionDefinition question, ValidationFiles.QuestionFiles files) {
        List<SubmissionIssue> issues = new ArrayList<>();
        for (QuestionValidationRule rule : questionRules) {
            issues.addAll(rule.validate(submission, question, files));
        }

        Path questionDirectory = files.expectedFolder().toAbsolutePath().normalize();
        List<Path> answerFiles = locateSubmittedAnswerFiles(question, issues, files);
        QuestionAttempt attempt = new QuestionAttempt(question, questionDirectory, answerFiles, issues);

        submission.addQuestionAttempt(attempt);
        //attempt keeps its own findings > submission also collects them for reporting & blocking
        submission.addIssues(issues);
    }

    private List<Path> locateSubmittedAnswerFiles(QuestionDefinition question, List<SubmissionIssue> issues, ValidationFiles.QuestionFiles files) {
        List<String> editableFiles;
        try {
            editableFiles = selectEditableFileNames(question);
        } catch (IOException error) {
            issues.add(SubmissionIssue.error("file read error", "unable to locate answer files > " + error.getMessage(), question.getId()));
            return List.of();
        }

        List<Path> found = new ArrayList<>();
        boolean failed = files.actualFolder().error() != null;
        for (String filename : editableFiles) {
            ValidationFiles.Lookup lookup = files.answer(filename);
            if (lookup.error() != null) {
                failed = true;
                //required lookup failures belong to answerfilerule > report only new optional-file failures here
                if (files.actualFolder().error() == null && !files.requiredAnswers().containsKey(filename)) issues.add(SubmissionIssue.error("file read error", "unable to locate answer files > " + lookup.error().getMessage(), question.getId()));
            } else lookup.path().ifPresent(path -> found.add(path.toAbsolutePath().normalize()));
        }
        return failed ? List.of() : found;
    }

    //required answers plus java helpers with student header labels are editable > missing helpers are optional

    private List<String> selectEditableFileNames(QuestionDefinition question) throws IOException {
        TemplateSelection selection = templateSelections.get(question);
        if (selection == null) {
            try {
                var editableFileNames = new LinkedHashSet<>(question.getRequiredAnswerFiles());
                for (String resource : question.getSupportFiles()) {
                    if (resource.endsWith(".java") && StudentHeaderReader.hasStudentHeader(question.getResourceDirectory().resolve(resource))) {
                        editableFileNames.add(resource);
                    }
                }
                selection = new TemplateSelection(List.copyOf(editableFileNames), null);
            } catch (IOException error) {
                selection = new TemplateSelection(List.of(), error);
            }
            templateSelections.put(question, selection);
        }
        if (selection.error() != null) throw selection.error();
        return selection.fileNames();
    }
}
