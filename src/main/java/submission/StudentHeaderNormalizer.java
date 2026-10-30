package submission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.StudentDetails;

final class StudentHeaderNormalizer { //fills missing name & email comments from the scoresheet > conflicting details are left for validation to report
    private static final String NAME_LABEL = "Name";
    private static final String EMAIL_LABEL = "Email(?:\\h+ID)?";
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
        byte[] original = Files.readAllBytes(file);
        //decoding is only for reading the header > bytes are kept so a non utf-8 file reaches javac unchanged
        String source = new String(original, StandardCharsets.UTF_8);
        String header = leadingComments(source.startsWith("﻿") ? source.substring(1) : source);
        List<String> names = values(header, NAME_LABEL);
        List<String> emails = values(header, EMAIL_LABEL);
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
        Files.write(file, updated.toByteArray());
        return true;
    }

    //a blank name line still counts > the template ships the section empty
    static boolean hasCommentSection(Path file) throws IOException {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String header = leadingComments(source.startsWith("﻿") ? source.substring(1) : source);
        return Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*(?:" + NAME_LABEL + "|" + EMAIL_LABEL + ")\\h*:",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(header).find();
    }

    private static boolean startsWithBom(byte[] content) {
        return content.length >= UTF8_BOM.length && content[0] == UTF8_BOM[0] && content[1] == UTF8_BOM[1] && content[2] == UTF8_BOM[2];
    }

    private static List<String> values(String header, String label) {
        Matcher matcher = Pattern.compile("^\\h*(?://|/\\*|\\*)?\\h*" + label + "\\h*:\\h*([^\\r\\n]*)$",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(header);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1).replaceFirst("\\h*\\*/\\h*$", "").strip();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String leadingComments(String source) {
        int position = 0;
        while (position < source.length()) {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
            if (source.startsWith("//", position)) {
                int end = source.indexOf('\n', position);
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
        return isUsable(expected) && values.stream().anyMatch(value -> !value.equalsIgnoreCase(expected.strip()));
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !value.contains("\r") && !value.contains("\n");
    }
}
