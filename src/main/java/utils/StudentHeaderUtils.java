package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StudentHeaderUtils { //reads student header fields for validation & file detection
    private static final int maxHeaderLines = 15;
    private static final Pattern leadingComments = Pattern.compile("\\A\\s*((?:(?://[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)\\s*)+)");
    private static final Pattern name = fieldPattern("Name");
    private static final Pattern email = fieldPattern("Email(?:\\h+ID)?");

    public record Header(String name, String email) {} //null means missing > an empty string means blank

    private StudentHeaderUtils() {}

    public static Header read(Path file) throws IOException {
        String header = readHeader(file);
        return new Header(readField(header, name), readField(header, email));
    }

    //@1ejun6 > detect field labels even when starter templates leave their values blank
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
