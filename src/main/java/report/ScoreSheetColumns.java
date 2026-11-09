package report;

import java.io.IOException;
import java.util.Arrays;

public final class ScoreSheetColumns { //defines shared student CSV column positions & header labels
    public static final int username = 0;
    public static final int gradeNumerator = 1;

    private ScoreSheetColumns() {
    }

    public static boolean isBlankRow(String[] row) {
        return Arrays.stream(row).allMatch(String::isBlank);
    }

    public static void validateHeader(String[] row) throws IOException {
        String[] expected = header();
        if (row.length < expected.length) throw new IOException("scoresheet header must contain username and grade columns");
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
            "Username", "Calculated Final Grade Numerator"
        };
    }
}
