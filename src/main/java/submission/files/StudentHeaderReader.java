//reads student names & emails from source text for validation & student list creation
//checks leading file comments for validation & finds nonblank source fields when building a student list

package submission.files;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StudentHeaderReader {
    private static final int maxHeaderLines = 15;
    private static final Pattern leadingComments = Pattern.compile("\\A\\s*((?:(?://[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)\\s*)+)");
    private static final Pattern nameField = fieldPattern("Name");
    private static final Pattern emailField = fieldPattern("Email(?:\\h+ID)?");

    //a null field is missing > an empty field has a label but no value
    public record Header(String name, String email) {}

    private StudentHeaderReader() {}

    //reads leading comments within first 15 lines > preserve missing versus blank fields
    public static Header read(Path file) throws IOException {
        String header = readHeader(file);
        return new Header(readField(header, nameField), readField(header, emailField));
    }

    //finds first nonblank name & email across source text when creating a student list when none is provided
    public static Header readFields(String source) {
        return new Header(firstNonBlankField(source, nameField), firstNonBlankField(source, emailField));
    }

    private static String firstNonBlankField(String source, Pattern pattern) {
        Matcher match = pattern.matcher(source);
        while (match.find()) {
            String value = match.group(1).strip();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    //blank template labels still identify a file intended for student editing
    public static boolean hasStudentHeader(Path file) throws IOException {
        Header header = read(file);
        return header.name() != null && header.email() != null;
    }

    private static String readField(String header, Pattern pattern) {
        Matcher match = pattern.matcher(header);
        return match.find() ? match.group(1).strip() : null;
    }

    private static Pattern fieldPattern(String label) {
        return Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*(.*?)\\h*(?:\\*/)?\\h*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    private static String readHeader(Path file) throws IOException {
        StringBuilder source = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            for (int count = 0; count < maxHeaderLines; count++) {
                String line = reader.readLine();
                if (line == null) break;
                source.append(line).append('\n');
            }
        }
        Matcher comments = leadingComments.matcher(source);
        return comments.find() ? comments.group(1) : "";
    }
}
