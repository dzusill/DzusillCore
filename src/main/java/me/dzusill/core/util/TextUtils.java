package me.dzusill.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * String helpers for command handling, most notably partial-match filtering used to build
 * tab-completion suggestions.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * Returns the candidates that start with {@code token}, case-insensitively. Used to narrow
     * tab-completion suggestions as the player types.
     */
    public static List<String> partialMatches(String token, Collection<String> candidates) {
        List<String> matches = new ArrayList<>();
        if (token == null) {
            return matches;
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.regionMatches(true, 0, token, 0, token.length())) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /**
     * Joins {@code args} from {@code fromIndex} (inclusive) to the end with spaces. Useful for
     * commands whose final argument is free-form text.
     */
    public static String joinFrom(String[] args, int fromIndex) {
        if (fromIndex >= args.length) {
            return "";
        }
        return String.join(" ", List.of(args).subList(fromIndex, args.length));
    }
}
