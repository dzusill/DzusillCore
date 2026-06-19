package me.dzusill.core.util;

import java.time.Duration;

/**
 * Formats durations into short, human-readable strings (e.g. {@code 1h 5m 3s}), primarily for cooldown and
 * remaining-time messages.
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /**
     * Formats a millisecond duration as a compact {@code Xd Yh Zm Ws} string, omitting zero units. Durations under one
     * second render as {@code 0s}.
     */
    public static String format(long millis) {
        if (millis < 1000) {
            return "0s";
        }
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        StringBuilder builder = new StringBuilder();
        appendUnit(builder, days, "d");
        appendUnit(builder, hours, "h");
        appendUnit(builder, minutes, "m");
        appendUnit(builder, seconds, "s");
        return builder.toString().trim();
    }

    private static void appendUnit(StringBuilder builder, long value, String suffix) {
        if (value > 0) {
            builder.append(value).append(suffix).append(' ');
        }
    }
}
