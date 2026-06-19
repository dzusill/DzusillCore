package me.dzusill.core.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An ordered, immutable-ish set of placeholder substitutions applied to raw message strings before MiniMessage parsing.
 * Supports both named tokens ({@code %player%}) and positional tokens ({@code {0}}), so existing config conventions
 * keep working.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * Placeholder.of("player", player.getName()).and("amount", "5");
 * }</pre>
 */
public final class Placeholder {

    private final Map<String, String> values = new LinkedHashMap<>();

    private Placeholder() {
    }

    /**
     * @return an empty placeholder set
     */
    public static Placeholder empty() {
        return new Placeholder();
    }

    /**
     * Creates a placeholder set with a single named entry.
     */
    public static Placeholder of(String key, Object value) {
        return new Placeholder().and(key, value);
    }

    /**
     * Creates a placeholder set from positional values, mapped to {@code {0}}, {@code {1}}, ...
     */
    public static Placeholder positional(Object... values) {
        Placeholder placeholder = new Placeholder();
        for (int i = 0; i < values.length; i++) {
            placeholder.and(String.valueOf(i), values[i]);
        }
        return placeholder;
    }

    /**
     * Adds a named substitution and returns {@code this} for chaining.
     */
    public Placeholder and(String key, Object value) {
        values.put(key, String.valueOf(value));
        return this;
    }

    /**
     * Applies every substitution to the given raw string, replacing both {@code %key%} and {@code {key}} forms.
     */
    public String apply(String input) {
        if (input == null || values.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
