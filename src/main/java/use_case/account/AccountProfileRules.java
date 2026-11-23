package use_case.account;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountProfileRules {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,24}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLANK_DISPLAY_NAME =
            Pattern.compile("[\\s\\p{javaWhitespace}]*", Pattern.UNICODE_CHARACTER_CLASS);

    private AccountProfileRules() { }

    public static boolean isUsername(String value) {
        return value != null && USERNAME.matcher(value).matches();
    }

    public static boolean isCanonicalUsername(String value) {
        return isUsername(value) && value.equals(value.toLowerCase(Locale.ROOT));
    }

    public static boolean hasDisplayName(String value) {
        return value != null && !BLANK_DISPLAY_NAME.matcher(value).matches();
    }
}
