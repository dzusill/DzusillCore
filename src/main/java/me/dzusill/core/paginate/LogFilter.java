package me.dzusill.core.paginate;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * What a staff member narrowed a log down to: a time window, and a piece of text to look for.
 *
 * <p>
 * Parsed from trailing {@code --since} / {@code --until} / {@code --find} flags so the ordinary case stays
 * {@code /oberonchat history Steve} with nothing to remember, and the narrowing is there when a log has grown past the
 * point where paging alone helps.
 * </p>
 *
 * @param since
 *            lower bound in epoch milliseconds, or empty for no lower bound
 * @param until
 *            upper bound in epoch milliseconds, or empty for no upper bound
 * @param find
 *            case-insensitive substring, or {@code null} for no text filter
 */
public record LogFilter(OptionalLong since, OptionalLong until, String find) {

    public static final String SINCE_FLAG = "--since";
    public static final String UNTIL_FLAG = "--until";
    public static final String FIND_FLAG = "--find";

    /** Bounded so a filter cannot itself become the pathological query. */
    private static final int MAX_FIND = 64;

    public LogFilter {
        since = since == null ? OptionalLong.empty() : since;
        until = until == null ? OptionalLong.empty() : until;
        find = find == null || find.isBlank() ? null : trim(find);
    }

    public static LogFilter none() {
        return new LogFilter(OptionalLong.empty(), OptionalLong.empty(), null);
    }

    public boolean isEmpty() {
        return since.isEmpty() && until.isEmpty() && find == null;
    }

    public boolean hasFind() {
        return find != null;
    }

    /** The {@code find} term as a SQL {@code LIKE} pattern, with the wildcards a player could type escaped. */
    public String likePattern() {
        String escaped = find.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    /**
     * Reads the flags out of an argument list.
     *
     * <p>
     * Unknown flags and unparseable values are reported rather than ignored. Silently dropping a filter is the worst
     * outcome here: a moderator would read a full log believing it was the filtered one.
     * </p>
     *
     * @param args
     *            the raw arguments, flags anywhere among them
     * @param now
     *            reference instant for relative forms
     * @param zone
     *            zone for absolute forms
     * @param problems
     *            collects the arguments that could not be understood
     */
    public static LogFilter parse(List<String> args, long now, ZoneId zone, List<String> problems) {
        OptionalLong since = OptionalLong.empty();
        OptionalLong until = OptionalLong.empty();
        String find = null;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!arg.startsWith("--")) {
                continue;
            }
            String value = i + 1 < args.size() ? args.get(i + 1) : null;
            if (value == null || value.startsWith("--")) {
                problems.add(arg + " (no value)");
                continue;
            }
            switch (arg.toLowerCase(Locale.ROOT)) {
                case SINCE_FLAG -> {
                    OptionalLong parsed = TimeSpec.parse(value, now, zone);
                    if (parsed.isEmpty()) {
                        problems.add(arg + " " + value);
                    } else {
                        since = parsed;
                    }
                }
                case UNTIL_FLAG -> {
                    OptionalLong parsed = TimeSpec.parse(value, now, zone);
                    if (parsed.isEmpty()) {
                        problems.add(arg + " " + value);
                    } else {
                        until = parsed;
                    }
                }
                case FIND_FLAG -> find = value;
                default -> problems.add(arg);
            }
            i++;
        }
        return new LogFilter(since, until, find);
    }

    /** The arguments with every flag and its value removed, so positional parsing can run on what is left. */
    public static List<String> stripFlags(List<String> args) {
        List<String> kept = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).startsWith("--")) {
                // Skip the flag, and its value when it has one.
                if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                    i++;
                }
                continue;
            }
            kept.add(args.get(i));
        }
        return kept;
    }

    /** Flags to suggest in tab completion. */
    public static List<String> flags() {
        return List.of(SINCE_FLAG, UNTIL_FLAG, FIND_FLAG);
    }

    /**
     * Bounded, and stripped of the handful of characters that would otherwise have to survive a round trip through a
     * MiniMessage {@code click:run_command} argument when the Prev/Next footer rebuilds the command. None of them mean
     * anything in a chat-log search, so dropping them costs nothing and removes the only way a search term could reach
     * the footer as markup.
     */
    private static String trim(String value) {
        String cleaned = value.trim().replaceAll("['\"<>\\\\]", "");
        return cleaned.length() > MAX_FIND ? cleaned.substring(0, MAX_FIND) : cleaned;
    }
}
