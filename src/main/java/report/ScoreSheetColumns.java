package report;

import java.io.IOException;
import java.util.Arrays;

public final class ScoreSheetColumns { //defines shared student CSV column positions & header labels
    public static final int orgDefinedId = 0;
    public static final int username = 1;
    public static final int lastName = 2;
    public static final int firstName = 3;
    public static final int email = 4;
    public static final int gradeNumerator = 5;
    public static final int gradeDenominator = 6;

    private ScoreSheetColumns() {
    }

    public static boolean isBlankRow(String[] row) {
        return Arrays.stream(row).allMatch(String::isBlank);
    }

    public static void validateHeader(String[] row) throws IOException {
        String[] expected = header();
        if (row.length <= email) throw new IOException("scoresheet header must contain student identity columns");
        for (int column = 0; column < Math.min(row.length, expected.length); column++) {
            if (!expected[column].equalsIgnoreCase(row[column].strip())) {
                throw new IOException("invalid scoresheet header at column " + (column + 1) + " > expected " + expected[column]);
            }
        }
    }

    public static String[] header() {
        return new String[] {
                "OrgDefinedId", "Username", "Last Name", "First Name", "Email", "Calculated Final Grade Numerator", "Calculated Final Grade Denominator"
        };
    }
}
