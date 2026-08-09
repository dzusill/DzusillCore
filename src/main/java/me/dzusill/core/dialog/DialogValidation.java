package me.dzusill.core.dialog;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared validation for the dialog value types.
 *
 * <p>
 * The rules here mirror the ones the vanilla codec enforces at command-parse time, verified empirically against Paper
 * 1.21.11 (see {@code DDIALOGS_SURVEY.md} §4.0). Validating up front turns a server-side
 * {@code Failed to parse structure} - which a plugin can neither see nor attribute - into an
 * {@link IllegalArgumentException} at the call site.
 * </p>
 *
 * <p>
 * One vanilla behaviour is deliberately <em>not</em> mirrored: the codec silently ignores unknown fields. There is no
 * equivalent leniency here, because an unknown field in a spec is always a bug.
 * </p>
 */
final class DialogValidation {

    /**
     * Vanilla: <em>"bad-key! is not a valid input name"</em>. Letters, digits and underscore only.
     */
    private static final Pattern INPUT_KEY = Pattern.compile("[A-Za-z0-9_]+");

    /** Reserved for control data the backend adds to the wire payload; user keys must not collide. */
    static final String RESERVED_KEY_PREFIX = "dz_";

    static final int MIN_WIDTH = 1;
    static final int MAX_WIDTH = 1024;
    static final int DEFAULT_BUTTON_WIDTH = 150;
    static final int DEFAULT_BODY_WIDTH = 200;
    static final int DEFAULT_INPUT_WIDTH = 200;
    static final int DEFAULT_TEXT_MAX_LENGTH = 32;
    static final int MIN_MULTILINE_HEIGHT = 1;
    static final int MAX_MULTILINE_HEIGHT = 512;

    private DialogValidation() {
    }

    static String requireText(String value, String what) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException(what + " must not be null or empty");
        return value;
    }

    static <T> T requireNonNull(T value, String what) {
        if (value == null)
            throw new IllegalArgumentException(what + " must not be null");
        return value;
    }

    /**
     * Validates an input key against the vanilla charset and the reserved-prefix rule.
     */
    static String requireInputKey(String key) {
        requireText(key, "input key");
        if (!INPUT_KEY.matcher(key).matches())
            throw new IllegalArgumentException(
                    "input key '" + key + "' is not valid: only letters, digits and underscore are allowed");
        if (key.toLowerCase(Locale.ROOT).startsWith(RESERVED_KEY_PREFIX))
            throw new IllegalArgumentException(
                    "input key '" + key + "' uses the reserved '" + RESERVED_KEY_PREFIX + "' prefix");
        return key;
    }

    /**
     * Clamps nothing - an out-of-range width is a programming error, not something to silently fix.
     */
    static int requireWidth(int width, String what) {
        if (width < MIN_WIDTH || width > MAX_WIDTH)
            throw new IllegalArgumentException(
                    what + " must be between " + MIN_WIDTH + " and " + MAX_WIDTH + ", got " + width);
        return width;
    }

    static int requireColumns(int columns) {
        if (columns < 1)
            throw new IllegalArgumentException("columns must be positive, got " + columns);
        return columns;
    }
}
