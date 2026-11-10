package utils.strings;

import java.util.Locale;

public class CommonPatternChecker {
    
    //Strip CSV decorations and normalize while preserving characters inside usernames.
    public static String normalizeUsername(String value) {
        return value.strip().replaceFirst("^#+_?", "").strip().toLowerCase(Locale.ROOT);
    }

}
