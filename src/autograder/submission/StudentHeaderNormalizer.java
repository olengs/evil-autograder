package autograder.submission;

import autograder.model.IssueSeverity;
import autograder.model.Student;
import autograder.model.SubmissionIssue;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AI-assisted with Codex: fills missing leading comments from a verified roster identity. */
final class StudentHeaderNormalizer {
    private StudentHeaderNormalizer() { }

    static List<SubmissionIssue> normalize(Path file, Student student, String questionId) throws IOException {
        String source = Files.readString(file);
        String bom = source.startsWith("\uFEFF") ? "\uFEFF" : "";
        String body = bom.isEmpty() ? source : source.substring(1);
        String header = leadingComments(body);
        List<String> names = values(header, "Name");
        List<String> emails = values(header, "Email(?:\\h+ID)?");
        if (conflicts(names, student.getName()) || conflicts(emails, student.getEmail())) {
            return List.of(new SubmissionIssue("conflicting student header", IssueSeverity.WARNING,
                    "existing name/email differs from the roster; header left unchanged > " + file, questionId, file));
        }
        boolean missingName = names.isEmpty() && usable(student.getName());
        boolean missingEmail = emails.isEmpty() && usable(student.getEmail());
        if (!missingName && !missingEmail) {
            return List.of();
        }
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder prefix = new StringBuilder();
        //Keep known identification fields at the top even if existing comments are close to the line limit.
        if (usable(student.getName())) { prefix.append("// Name: ").append(student.getName().strip()).append(newline); }
        if (usable(student.getEmail())) { prefix.append("// Email ID: ").append(student.getEmail().strip()).append(newline); }
        Path temporary = Files.createTempFile(file.getParent(), ".header-", ".tmp");
        try {
            Files.writeString(temporary, bom + prefix + body);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return List.of(new SubmissionIssue("student header normalized", IssueSeverity.WARNING,
                "added missing identification comments from roster > " + file, questionId, file));
    }

    private static List<String> values(String header, String label) {
        Matcher matcher = Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*([^\\r\\n]*)$",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(header);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1).replaceFirst("\\h*\\*/\\h*$", "").strip();
            if (!value.isEmpty()) { values.add(value); }
        }
        return values;
    }

    private static String leadingComments(String source) {
        int position = 0;
        while (position < source.length()) {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) { position++; }
            if (source.startsWith("//", position)) {
                int end = source.indexOf('\n', position + 2);
                position = end < 0 ? source.length() : end + 1;
            } else if (source.startsWith("/*", position)) {
                int end = source.indexOf("*/", position + 2);
                position = end < 0 ? source.length() : end + 2;
            } else {
                break;
            }
        }
        return source.substring(0, position);
    }

    private static boolean conflicts(List<String> values, String expected) {
        return usable(expected) && values.stream().anyMatch(value -> !value.equalsIgnoreCase(expected.strip()));
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && !value.contains("\r") && !value.contains("\n");
    }
}
