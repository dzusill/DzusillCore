package me.dzusill.core.message;

import java.util.Locale;

/**
 * Where a message is shown.
 *
 * <p>
 * The action bar is one line the whole server shares — coordinates, combat timers and boss-bar substitutes all live
 * there — so a plugin that writes into it is competing with everything else that does. That is a reason to choose per
 * message rather than a reason to avoid it: a toggle confirmation belongs there precisely because it is worth one
 * glance and nothing more, while a list of ignored players does not.
 * </p>
 */
public enum MessageChannel {

    /** Chat, as every message was sent before this existed. */
    CHAT,
    /** Above the hotbar. One line, no history, gone in a couple of seconds. */
    ACTION_BAR,
    /** Both — for something that must be noticed now and still be findable by scrolling back. */
    BOTH,
    /** Nowhere. The message is built and dropped; a way to silence one line without deleting it. */
    NONE;

    public boolean chat() {
        return this == CHAT || this == BOTH;
    }

    public boolean actionBar() {
        return this == ACTION_BAR || this == BOTH;
    }

    /**
     * @return the named channel, or {@code fallback} for a name that names none — a typo in a config must not stop a
     *         plugin from starting
     */
    public static MessageChannel fromString(String name, MessageChannel fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException notAChannel) {
            return fallback;
        }
    }
}
