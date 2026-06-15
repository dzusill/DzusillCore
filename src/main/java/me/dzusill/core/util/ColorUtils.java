package me.dzusill.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Static helpers for turning configuration strings into Adventure {@link Component}s. Prefers
 * MiniMessage but bridges the legacy {@code &}-code format so older configs keep working.
 *
 * <p>All returned text components have italics disabled by default, matching the convention
 * expected for item names and lore (Minecraft italicizes custom names otherwise).</p>
 */
public final class ColorUtils {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ColorUtils() {
    }

    /**
     * Parses a MiniMessage string into a component with default item-safe (non-italic) styling.
     */
    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MINI.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Parses every line of a list into item-safe components, intended for lore.
     */
    public static List<Component> parse(List<String> input) {
        List<Component> result = new ArrayList<>(input.size());
        for (String line : input) {
            result.add(parse(line));
        }
        return result;
    }

    /**
     * Parses a string that may use legacy {@code &} color codes into a component.
     */
    public static Component legacy(String input) {
        if (input == null) {
            return Component.empty();
        }
        return LEGACY_AMP.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Strips all formatting and returns the plain text content of a parsed MiniMessage string.
     */
    public static String strip(String input) {
        return input == null ? "" : PLAIN.serialize(MINI.deserialize(input));
    }
}
