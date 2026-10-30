package validation.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentDetails;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.SubmissionFileFinder;
import validation.ValidationRule;

public class StudentHeaderRule implements ValidationRule { //checks student name & email in comments at the top of each required answer
    private static final int headerLineMax = 15; //@1ejun6 > sets how many lines to inspect student java file
    private final SubmissionFileFinder fileLocator;

    public StudentHeaderRule(SubmissionFileFinder fileLocator) {
        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        if (!Files.isDirectory(questionFolder)) {
            return issues;
        }

        StudentDetails student = submission.getStudent();

        if (student == null) {
            issues.add(new SubmissionIssue("unknown student", IssueSeverity.WARNING, "cannot check headers without a matched student", question.getId(), questionFolder));
            return issues;
        }

        for (String requiredFile : question.getEditableFiles()) {
            try {
                Optional<Path> file = fileLocator.findExact(questionFolder, requiredFile);
                if (file.isEmpty()) file = fileLocator.findIgnoringCase(questionFolder, requiredFile);

                if (file.isEmpty()) {
                    continue;
                }

                String header = readHeader(file.get());

                checkField(header, "Name", "Name", student.getName(), question.getId(), file.get(), issues);
                checkField(header, "Email(?:\\h+ID)?", "Email", student.getEmail(), question.getId(), file.get(), issues);
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR, "unable to read " + requiredFile + " > " + e.getMessage(), question.getId(), questionFolder.resolve(requiredFile)));
            }
        }
        return issues;
    }

    private void checkField(String header, String label, String field, String expected,
            String questionId, Path file, List<SubmissionIssue> issues) {
        //@1ejun6 > read actual header value > distinguish missing fields from mismatched values
        String regex = "^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*(.*?)\\h*(?:\\*/)?\\h*$";
        Matcher match = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE).matcher(header);
        String found = match.find() ? match.group(1).strip() : null;
        boolean missing = found == null || found.isEmpty();
        if (missing || expected == null || !found.equalsIgnoreCase(expected.strip())) {
            String value = found == null ? "(missing)" : found.isEmpty() ? "(blank)" : found;
            issues.add(new SubmissionIssue(missing ? "missing student header" : "mismatched student header",
                    IssueSeverity.WARNING, file.getFileName() + " > " + field + ": expected [" + expected
                    + "] but found [" + value + "]", questionId, file));
        }
    }

    private String readHeader(Path file) throws IOException {
        StringBuilder header = new StringBuilder();

        //inputstreamreader replaces bytes that are not utf-8 > a non utf-8 comment must not block grading
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;

            while (count < headerLineMax && (line = reader.readLine()) != null) {
                header.append(line).append(System.lineSeparator());
                count++;
            }
        }
        String text = header.toString().replaceFirst("^\\uFEFF", "");
        Matcher comments = Pattern.compile("\\A\\s*((?:(?://[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)\\s*)+)").matcher(text);
        return comments.find() ? comments.group(1) : "";
    }
}
