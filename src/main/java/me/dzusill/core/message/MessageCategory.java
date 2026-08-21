package me.dzusill.core.message;

import java.util.Locale;

/**
 * What kind of thing a message is, so a server owner can style all of one kind at once.
 *
 * <p>
 * Categories exist because the alternative is configuring seventy message keys individually to say the same four
 * things. A key that is not categorised is {@link #INFO}, which is plain chat — so a plugin that never categorises
 * anything behaves exactly as it did before any of this existed.
 * </p>
 */
public enum MessageCategory {

    /** A preference was switched on or off. Worth one glance; not worth a line of chat history. */
    TOGGLE,
    /** A transaction completed, such as selling items or receiving a payout. */
    SALE,
    /** Something was refused. The one kind worth showing twice, because it explains why nothing happened. */
    ERROR,
    /** A teleport happened. */
    TELEPORT,
    /** Everything else, and the default. */
    INFO;

    /** @return the named category, or {@code null} for a name that names none */
    public static MessageCategory fromString(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException notACategory) {
            return null;
        }
    }
}
