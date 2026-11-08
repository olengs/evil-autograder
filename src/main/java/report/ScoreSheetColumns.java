package report;

import java.io.IOException;
import java.util.Arrays;

public final class ScoreSheetColumns { //defines shared student CSV column positions & header labels
    public static final int orgDefinedId = 0;
    public static final int username = 1;
    public static final int email = 2;
    public static final int name = 3;
    public static final int gradeNumerator = 4;
    public static final int gradeDenominator = 5;

    private ScoreSheetColumns() {
    }

    public static boolean isBlankRow(String[] row) {
        return Arrays.stream(row).allMatch(String::isBlank);
    }

    //Strip CSV decorations consistently while preserving characters inside usernames.
    public static String normalizeUsername(String value) {
        return value.strip().replaceFirst("^#+_?", "").strip();
    }

    public static void validateHeader(String[] row) throws IOException {
        String[] expected = header();
        if (row.length <= email) throw new IOException("scoresheet header must contain student identity columns");
        for (int column = 0; column < Math.min(row.length, expected.length); column++) {
            if (!expected[column].equalsIgnoreCase(row[column].strip())) {
                throw new IOException(
                    String.format("invalid scoresheet header at column %d > got \"%s\", expected \"%s\"", column + 1, row[column], expected[column])
                );
            }
        }
    }

    public static String[] header() {
        return new String[] {
            "OrgDefinedId", "Username", "Email", "Name", "Calculated Final Grade Numerator", "Calculated Final Grade Denominator"
        };
    }
}
