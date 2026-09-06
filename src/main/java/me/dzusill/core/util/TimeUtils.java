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
     * Only <em>leading</em> zero units are dropped. Once the largest non-zero unit is found, every smaller one is
     * printed down to seconds even when it is zero — so {@code 5h 0m 42s} keeps its minutes and an exact hour reads
     * {@code 1h 0m 0s}, while {@code 52m 42s} never grows an {@code 0h} in front and half a minute is just {@code 30s}.
     * </p>
     *
     * <p>
     * The width therefore only ever changes when the duration crosses a unit boundary, not on an ordinary tick: a
     * countdown that dropped from {@code 5h 1m 42s} to {@code 5h 42s} looked like it had lost a column.
     * </p>
     */
    public static String format(long millis) {
        if (millis < 1000) {
            return "0s";
        }
        Duration duration = Duration.ofMillis(millis);
        long[] units = {duration.toDays(), duration.toHoursPart(), duration.toMinutesPart(), duration.toSecondsPart()};

        int first = 0;
        while (first < units.length - 1 && units[first] == 0) {
            first++;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = first; i < units.length; i++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(units[i]).append(SUFFIXES[i]);
        }
        return builder.toString();
    }
}
