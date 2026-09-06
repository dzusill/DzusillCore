package me.dzusill.core.util;

import java.time.Duration;

/**
 * Formats durations into short, human-readable strings (e.g. {@code 1h 5m 3s}), primarily for cooldown and
 * remaining-time messages.
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    private static final String[] SUFFIXES = {"d", "h", "m", "s"};

    /**
     * Formats a millisecond duration as a compact {@code Xd Yh Zm Ws} string. Durations under one second render as
     * {@code 0s}.
     *
     * <p>
     * Zero units are trimmed from the <em>ends</em> only — a zero between two non-zero units is kept, because a
     * countdown that jumps from {@code 5h 1m 42s} to {@code 5h 42s} appears to lose a column and reads as a glitch. So
     * {@code 5h 0m 42s} keeps its minutes while {@code 52m 42s} never grows an {@code 0h} in front, and an exact hour
     * stays {@code 1h} rather than {@code 1h 0m 0s}.
     * </p>
     */
    public static String format(long millis) {
        if (millis < 1000) {
            return "0s";
        }
        Duration duration = Duration.ofMillis(millis);
        long[] units = {duration.toDays(), duration.toHoursPart(), duration.toMinutesPart(), duration.toSecondsPart()};

        int first = 0;
        while (first < units.length && units[first] == 0) {
            first++;
        }
        int last = units.length - 1;
        while (last > first && units[last] == 0) {
            last--;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = first; i <= last; i++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(units[i]).append(SUFFIXES[i]);
        }
        return builder.toString();
    }
}
