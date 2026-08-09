package me.dzusill.core.dialog.spi;

import java.util.Map;
import java.util.Optional;

/**
 * The values a player submitted with a dialog.
 *
 * <p>
 * An interface rather than a record so a rendering backend can wrap the server's own response object with no copying,
 * while tests supply a trivial map-backed implementation.
 * </p>
 *
 * <p>
 * <strong>Read by declared type.</strong> A checkbox submits a byte and a slider submits a float, so
 * {@link #text(String)} on either yields {@link Optional#empty()}. Ask for what the input actually is. Every accessor
 * is empty-tolerant: an untouched control with no initial value may be absent from the payload entirely.
 * </p>
 *
 * <p>
 * <strong>Never trust these values.</strong> They arrive in a client-initiated packet. Re-validate length, charset and
 * range server-side before acting, no matter what the spec declared.
 * </p>
 */
public interface DialogValues {

    Optional<String> text(String key);

    Optional<Boolean> flag(String key);

    Optional<Float> number(String key);

    default String textOr(String key, String fallback) {
        return text(key).orElse(fallback);
    }

    default boolean flagOr(String key, boolean fallback) {
        return flag(key).orElse(fallback);
    }

    /**
     * Reads a slider as a whole number. Sliders always submit floats, so this rounds rather than truncating.
     */
    default int intOr(String key, int fallback) {
        return number(key).map(Math::round).orElse(fallback);
    }

    static DialogValues empty() {
        return of(Map.of());
    }

    /**
     * Map-backed implementation for tests and for backends that have already decoded the payload.
     *
     * <p>
     * Values are read by runtime type, so a {@code Boolean} is only visible through {@link #flag(String)} and a
     * {@code Number} only through {@link #number(String)} - matching how the real payload behaves.
     * </p>
     */
    static DialogValues of(Map<String, Object> raw) {
        Map<String, Object> copy = Map.copyOf(raw);
        return new DialogValues() {

            @Override
            public Optional<String> text(String key) {
                Object value = copy.get(key);
                return value instanceof String string ? Optional.of(string) : Optional.empty();
            }

            @Override
            public Optional<Boolean> flag(String key) {
                Object value = copy.get(key);
                return value instanceof Boolean bool ? Optional.of(bool) : Optional.empty();
            }

            @Override
            public Optional<Float> number(String key) {
                Object value = copy.get(key);
                return value instanceof Number number ? Optional.of(number.floatValue()) : Optional.empty();
            }

            @Override
            public String toString() {
                return "DialogValues" + copy;
            }
        };
    }
}
