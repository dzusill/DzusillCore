package me.dzusill.core.command.argument;

import java.util.Map;

/**
 * Typed, read-only view over the values produced by {@link ArgumentParser}. Command logic pulls already-parsed values
 * by name without re-validating raw strings.
 */
public final class Arguments {

    private final Map<String, Object> values;

    Arguments(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * @return {@code true} if a value was parsed for {@code name} (always {@code true} for required arguments,
     *         conditional for optional ones)
     */
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /**
     * @return the parsed value, or {@code null} if absent (optional argument not supplied)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        return (T) values.get(name);
    }

    /**
     * @return the parsed value, or {@code fallback} if the argument was not supplied
     */
    public <T> T getOr(String name, T fallback) {
        T value = get(name);
        return value != null ? value : fallback;
    }

    public String getString(String name) {
        return get(name);
    }

    public int getInt(String name) {
        return this.<Integer>get(name);
    }
}
