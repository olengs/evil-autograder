package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.AssessmentDefinition;
import model.IssueSeverity;
import model.QuestionDefinition;
import model.StudentDetails;
import model.SubmissionIssue;

/**
 * adds a missing name or email to the top of each answer file using the scoresheet
 * only edits the working copy > a header naming someone else is left for StudentHeaderRule to report
 */
class StudentHeaderNormalizer {
    private static final Pattern LEADING_COMMENTS = Pattern.compile("\\A\\s*(?:(?://[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)\\s*)+");
    private static final Pattern NAME = fieldPattern("Name");
    private static final Pattern EMAIL = fieldPattern("Email(?:\\h+ID)?");

    static List<SubmissionIssue> fillMissingHeaders(Path root, AssessmentDefinition assessment, StudentDetails student) {
        List<SubmissionIssue> issues = new ArrayList<>();
        if (student == null) {
            return issues; //an unknown student has no scoresheet details to write
        }
        for (QuestionDefinition question : assessment.getQuestions()) {
            for (String requiredFile : question.getRequiredFiles()) {
                Path file = root.resolve(question.getExpectedFolder()).resolve(requiredFile);
                try {
                    if (Files.isRegularFile(file) && fillMissingFields(file, student)) {
                        issues.add(new SubmissionIssue("student header normalized", IssueSeverity.WARNING, "added missing details from scoresheet > " + requiredFile, question.getId(), file));
                    }
                } catch (IOException error) {
                    issues.add(new SubmissionIssue("normalization error", IssueSeverity.ERROR, error.toString(), question.getId(), file));
                }
            }
        }
        return issues;
    }

    private static boolean fillMissingFields(Path file, StudentDetails student) throws IOException {
        String source = Files.readString(file);
        Matcher comments = LEADING_COMMENTS.matcher(source);
        String header = comments.find() ? comments.group() : "";
        String name = fieldValue(header, NAME);
        String email = fieldValue(header, EMAIL);
        if (differs(name, student.getName()) || differs(email, student.getEmail())) {
            return false; //possible copied work > validation reports it
        }

        StringBuilder missing = new StringBuilder();
        if (name.isEmpty() && hasText(student.getName())) {
            missing.append("// Name: ").append(student.getName().strip()).append('\n');
        }
        if (email.isEmpty() && hasText(student.getEmail())) {
            missing.append("// Email ID: ").append(student.getEmail().strip()).append('\n');
        }
        if (missing.isEmpty()) {
            return false;
        }
        Files.writeString(file, missing.toString() + source);
        return true;
    }

    private static Pattern fieldPattern(String label) {
        return Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*(.*?)\\h*(?:\\*/)?\\h*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    //a blank field reads as empty > the template ships the section blank
    private static String fieldValue(String header, Pattern field) {
        Matcher matcher = field.matcher(header);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static boolean differs(String found, String expected) {
        return !found.isEmpty() && hasText(expected) && !found.equalsIgnoreCase(expected.strip());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
