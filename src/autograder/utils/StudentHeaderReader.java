// Reads leading identity comments from source files.
// Shares the existing 15-line header-inspection rules.

package autograder.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StudentHeaderReader {
    private static final int HEADER_LINE_MAX = 15;
    private static final Pattern HEADER_FIELD = Pattern.compile(
            "^\\h*(?://|/\\*|\\*)?\\h*(?:Name|Email(?:\\h+ID)?)\\h*:",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private StudentHeaderReader() {}

    /** A trusted template with a name or email section is intended to be edited. */
    public static boolean hasHeaderSection(Path file) throws IOException {
        return HEADER_FIELD.matcher(readLeadingComments(file)).find();
    }

    /** Keeps the existing first-15-lines, leading-comments-only reading behavior. */
    public static String readLeadingComments(Path file) throws IOException {
        StringBuilder header = new StringBuilder();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int count = 0;

            while (count < HEADER_LINE_MAX && (line = reader.readLine()) != null) {
                header.append(line).append(System.lineSeparator());
                count++;
            }
        }
        Matcher comments = Pattern.compile("\\A\\s*((?:(?://[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)\\s*)+)")
                .matcher(header);
        return comments.find() ? comments.group(1) : "";
    }
}
