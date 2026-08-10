package me.dzusill.core.paginate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the point in time a staff member typed, in either of the two forms they actually reach for.
 *
 * <p>
 * <b>Relative</b> - {@code 30m}, {@code 24h}, {@code 7d}, {@code 2w} - meaning "that long ago". This is what somebody
 * chasing a report types, because they know it happened this afternoon and not the date.
 * </p>
 *
 * <p>
 * <b>Absolute</b> - {@code 2026-08-01} or {@code 2026-08-01 14:30} - meaning midnight (or that minute) local time. This
 * is what somebody working from a ticket types.
 * </p>
 *
 * <p>
 * Both are resolved against the server's own zone, because that is the clock the staff member reading the output is on.
 * Nothing here throws: an unparseable string comes back empty, so the command can say so rather than the player getting
 * a stack trace for a typo.
 * </p>
 */
public final class TimeSpec {

    private static final Pattern RELATIVE = Pattern.compile("(?i)^(\\d+)\\s*([smhdw])$");
    private static final Pattern DATE_ONLY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ tT]\\d{2}:\\d{2}(:\\d{2})?$");

    private TimeSpec() {
    }

    /**
     * Resolves {@code input} to an epoch-millisecond instant.
     *
     * @param now
     *            the reference for relative forms, passed in rather than read so this is testable
     * @param zone
     *            the zone absolute forms are read in - normally {@link ZoneId#systemDefault()}
     * @return the instant, or empty when {@code input} is blank or not a form we recognise
     */
    public static OptionalLong parse(String input, long now, ZoneId zone) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }
        String text = input.trim();

        Matcher relative = RELATIVE.matcher(text);
        if (relative.matches()) {
            long amount;
            try {
                amount = Long.parseLong(relative.group(1));
            } catch (NumberFormatException tooBig) {
                // "99999999999999d" — a number nobody meant. Treat it as unparseable rather than overflowing to a
                // silently wrong instant.
                return OptionalLong.empty();
            }
            long millis = amount * unitMillis(relative.group(2));
            return OptionalLong.of(now - millis);
        }

        try {
            if (DATE_ONLY.matcher(text).matches()) {
                return OptionalLong.of(LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli());
            }
            if (DATE_TIME.matcher(text).matches()) {
                String normalised = text.replace(' ', 'T').replace('t', 'T');
                LocalDateTime parsed = normalised.length() == 16
                        ? LocalDateTime.parse(normalised + ":00")
                        : LocalDateTime.parse(normalised);
                return OptionalLong.of(parsed.atZone(zone).toInstant().toEpochMilli());
            }
        } catch (DateTimeParseException notADate) {
            // "2026-13-45" matches the shape but is not a date. Same answer as any other typo.
            return OptionalLong.empty();
        }
        return OptionalLong.empty();
    }

    /** Convenience for the common case: relative to the real clock, in the server's zone. */
    public static OptionalLong parse(String input) {
        return parse(input, System.currentTimeMillis(), ZoneId.systemDefault());
    }

    /** The forms to suggest in tab completion. */
    public static java.util.List<String> suggestions() {
        return java.util.List.of("30m", "1h", "6h", "24h", "7d", "30d");
    }

    private static long unitMillis(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "w" -> 604_800_000L;
            default -> 86_400_000L;
        };
    }

    /** Formats an instant the same way {@link #parse} reads it back, for echoing a filter in a header. */
    public static Optional<String> format(long epochMillis, ZoneId zone) {
        try {
            return Optional.of(java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        } catch (RuntimeException unformattable) {
            return Optional.empty();
        }
    }
}
