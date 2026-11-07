//defines column positions & header labels used by student list & output scoresheets
//keeps student list reading & student list creation & grade writing aligned with same csv layout

package report;

public final class ScoreSheetColumns {
    public static final int orgDefinedId = 0;
    public static final int username = 1;
    public static final int lastName = 2;
    public static final int firstName = 3;
    public static final int email = 4;
    public static final int gradeNumerator = 5;
    public static final int gradeDenominator = 6;
    public static final int endOfLine = 7;

    private ScoreSheetColumns() {
    }

    public static String[] header() {
        return new String[] {
                "OrgDefinedId", "Username", "Last Name", "First Name", "Email", "Calculated Final Grade Numerator", "Calculated Final Grade Denominator", "End-of-Line Indicator"
        };
    }
}
