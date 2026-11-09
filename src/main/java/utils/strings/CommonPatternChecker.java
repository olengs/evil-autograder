package utils.strings;

import java.util.regex.Pattern;

public class CommonPatternChecker {
    
    private static final String USERNAME_REGEX = "^[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})*\\.\\d{4}$";
    private static final Pattern USERNAME_PATTERN = Pattern.compile(USERNAME_REGEX);

    public static boolean ofUsernamePattern(String input) {
        return USERNAME_PATTERN.matcher(input).matches();
    }

}
