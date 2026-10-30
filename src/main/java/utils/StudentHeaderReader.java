package utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reads identity fields from leading comments without changing files or deciding validity. */
public final class StudentHeaderReader {
    private static final Pattern NAME = fieldPattern("Name");
    private static final Pattern EMAIL = fieldPattern("Email(?:\\h+ID)?");

    private StudentHeaderReader() { }

    /** Blank values and repeated fields are retained for the caller to interpret. */
    public record Header(List<String> names, List<String> emails) {
        public Header {
            names = List.copyOf(names);
            emails = List.copyOf(emails);
        }

        public boolean hasCommentSection() {
            return !names.isEmpty() || !emails.isEmpty();
        }
    }

    public static Header read(Path file) throws IOException {
        return parse(FileUtils.readBytes(file));
    }

    /** Decode only for inspection; malformed UTF-8 is replaced, and source bytes are untouched. */
    public static Header parse(byte[] content) {
        String source = new String(content, StandardCharsets.UTF_8);
        if (source.startsWith("\uFEFF")) source = source.substring(1);
        source = source.replace("\r\n", "\n").replace('\r', '\n');
        String comments = leadingComments(source);
        return new Header(values(comments, NAME), values(comments, EMAIL));
    }

    private static Pattern fieldPattern(String label) {
        return Pattern.compile("^\\h*\\*?\\h*" + label + "\\h*:\\h*([^\\n]*)$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    private static List<String> values(String comments, Pattern pattern) {
        var matches = pattern.matcher(comments);
        List<String> values = new ArrayList<>();
        while (matches.find()) values.add(matches.group(1).strip());
        return values;
    }

    // Read every complete leading comment, stopping at the first Java token.
    private static String leadingComments(String source) {
        StringBuilder comments = new StringBuilder();
        int position = 0;
        while (position < source.length()) {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
            if (source.startsWith("//", position)) {
                int end = source.indexOf('\n', position + 2);
                if (end < 0) end = source.length();
                comments.append(source, position + 2, end).append('\n');
                position = end;
            } else if (source.startsWith("/*", position)) {
                int end = source.indexOf("*/", position + 2);
                if (end < 0) break;
                comments.append(source, position + 2, end).append('\n');
                position = end + 2;
            } else {
                break;
            }
        }
        return comments.toString();
    }
}
