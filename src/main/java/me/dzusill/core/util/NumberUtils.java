package me.dzusill.core.util;

import java.text.DecimalFormat;
import java.util.Optional;

/**
 * Numeric helpers: safe parsing that never throws, and compact abbreviation of large numbers
 * (e.g. {@code 1.2K}, {@code 3.4M}).
 */
public final class NumberUtils {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};
    private static final DecimalFormat ABBREVIATION_FORMAT = new DecimalFormat("#.##");

    private NumberUtils() {
    }

    /**
     * Parses an integer without throwing.
     *
     * @return the parsed value, or empty if {@code input} is not a valid integer
     */
    public static Optional<Integer> parseInt(String input) {
        try {
            return Optional.of(Integer.parseInt(input.trim()));
        } catch (NumberFormatException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    /**
     * Parses a double without throwing.
     */
    public static Optional<Double> parseDouble(String input) {
        try {
            return Optional.of(Double.parseDouble(input.trim()));
        } catch (NumberFormatException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    /**
     * Abbreviates a number using K/M/B/T/Q suffixes (e.g. {@code 1500 -> "1.5K"}).
     */
    public static String abbreviate(double value) {
        double working = value;
        int index = 0;
        while (Math.abs(working) >= 1000 && index < SUFFIXES.length - 1) {
            working /= 1000;
            index++;
        }
        return ABBREVIATION_FORMAT.format(working) + SUFFIXES[index];
    }
}
