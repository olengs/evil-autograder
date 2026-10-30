package submission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import model.StudentDetails;
import utils.FileUtils;
import utils.StudentHeaderReader;

final class StudentHeaderNormalizer { //fills missing name & email comments from the scoresheet > conflicting details are left for validation to report
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private StudentHeaderNormalizer() {
    }

    /**
     * @param file > required answer file in the working copy
     * @param student > student matched from the scoresheet
     * @return > true when the file was changed
     * @throws IOException > if the file cannot be read \ written
     */
    static boolean addMissingFields(Path file, StudentDetails student) throws IOException {
        byte[] original = FileUtils.readBytes(file);
        // Keep the original bytes; decoding here only selects line endings for inserted comments.
        String source = new String(original, StandardCharsets.UTF_8);
        var header = StudentHeaderReader.parse(original);
        List<String> names = header.names().stream().filter(value -> !value.isBlank()).toList();
        List<String> emails = header.emails().stream().filter(value -> !value.isBlank()).toList();
        if (conflicts(names, student.getName()) || conflicts(emails, student.getEmail())) {
            return false; //possible copied work > validation reports it
        }

        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder missing = new StringBuilder();
        if (names.isEmpty() && isUsable(student.getName())) {
            missing.append("// Name: ").append(student.getName().strip()).append(newline);
        }
        if (emails.isEmpty() && isUsable(student.getEmail())) {
            missing.append("// Email ID: ").append(student.getEmail().strip()).append(newline);
        }
        if (missing.isEmpty()) {
            return false;
        }

        int bomLength = startsWithBom(original) ? UTF8_BOM.length : 0;
        ByteArrayOutputStream updated = new ByteArrayOutputStream(original.length + missing.length());
        updated.write(original, 0, bomLength);
        updated.writeBytes(missing.toString().getBytes(StandardCharsets.UTF_8));
        updated.write(original, bomLength, original.length - bomLength);
        FileUtils.writeBytes(file, updated.toByteArray());
        return true;
    }

    private static boolean startsWithBom(byte[] content) {
        return content.length >= UTF8_BOM.length && content[0] == UTF8_BOM[0] && content[1] == UTF8_BOM[1] && content[2] == UTF8_BOM[2];
    }

    private static boolean conflicts(List<String> values, String expected) {
        return isUsable(expected) && values.stream().anyMatch(value -> !value.equalsIgnoreCase(expected.strip()));
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !value.contains("\r") && !value.contains("\n");
    }
}
