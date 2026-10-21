package validation.rules;

import model.IssueSeverity;
import model.QuestionDefinition;
import model.Student;
import model.StudentSubmission;
import model.SubmissionIssue;
import validation.AnswerFileLocator;
import validation.ValidationRule;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StudentHeaderRule implements ValidationRule {
    private static final int headerLineMax = 15; //sets how many lines to inspect student java file
    private final AnswerFileLocator fileLocator;

    public StudentHeaderRule(AnswerFileLocator fileLocator) {
        this.fileLocator = fileLocator;
    }

    @Override
    public List<SubmissionIssue> validate(StudentSubmission submission, QuestionDefinition question) {
        List<SubmissionIssue> issues = new ArrayList<>();
        Path questionFolder = submission.getRootDirectory().resolve(question.getExpectedFolder());

        if (!Files.isDirectory(questionFolder)) {
            return issues;
        }

        Student student = submission.getStudent();

        if (student == null) {
            issues.add(new SubmissionIssue("unknown student", IssueSeverity.WARNING,
                    "cannot check headers without a matched student", question.getId(), questionFolder));
            return issues;
        }

        for (String requiredFile : question.getRequiredFiles()) {
            try {
                Optional<Path> file = fileLocator.findExact(questionFolder, requiredFile);

                if (file.isEmpty()) {
                    continue;
                }

                String header = readHeader(file.get());

                boolean containsName = containsField(header, "Name", student.getName());
                boolean containsEmail = containsField(header, "Email(?:\\h+ID)?", student.getEmail());

                if (!containsName || !containsEmail) {
                    issues.add(new SubmissionIssue("missing student header", IssueSeverity.WARNING, "student name ||| email "
                                    + "was not found near " + "top of " + requiredFile + ".",
                            question.getId(), file.get()));
                }
            } catch (IOException e) {
                issues.add(new SubmissionIssue("file read error", IssueSeverity.ERROR,
                        "unable to read " + requiredFile + ".", question.getId(),
                        questionFolder.resolve(requiredFile)));
            }
        }
        return issues;
    }

    private boolean containsField(String header, String label, String expected) {
        return expected != null && !expected.isBlank()
                && Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*"
                        + Pattern.quote(expected.strip()) + "\\h*(?:\\*/)?\\h*$",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE)
                        .matcher(header).find();
    }

    private String readHeader(Path file) throws IOException {
        StringBuilder header = new StringBuilder();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int count = 0;

            while (count < headerLineMax && (line = reader.readLine()) != null) {
                header.append(line).append(System.lineSeparator());
                count++;
            }
        }
        Matcher comments = Pattern.compile("\\A\\s*((?:(?://[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)\\s*)+)")
                .matcher(header);
        return comments.find() ? comments.group(1) : "";
    }
}
