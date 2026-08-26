package me.dzusill.core.util;

import java.util.ArrayList;
import java.util.List;

import me.dzusill.core.message.LegacyCodes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Static helpers for turning configuration strings into Adventure {@link Component}s. Prefers MiniMessage but bridges
 * the legacy {@code &}-code format so older configs keep working.
 *
 * <p>
 * All returned text components have italics disabled by default, matching the convention expected for item names and
 * lore (Minecraft italicizes custom names otherwise).
 * </p>
 */
public final class ColorUtils {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    /**
     * The Bukkit string APIs still used by menus and item metadata can carry RGB colours in Spigot's
     * {@code §x§R§R§G§G§B§B} form. The serializer factory does not enable that form by default and instead reduces
     * every RGB colour to one of the old sixteen colours.
     */
    private static final LegacyComponentSerializer LEGACY_SECTION_RGB = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ColorUtils() {
    }

    /**
     * Parses a MiniMessage string into a component with default item-safe (non-italic) styling.
     *
     * <p>
     * Legacy {@code &} codes, {@code &#RRGGBB} and a bare {@code #RRGGBB} are all accepted too, through
     * {@link LegacyCodes#toMiniMessage}. Owners write these interchangeably across one config file, and MiniMessage on
     * its own renders every one of them as literal text - the reported symptom being a tooltip that reads
     * {@code #00F986Ores} rather than colouring anything. Tags already written as MiniMessage, gradients included, are
     * copied through untouched, so this is safe on a string that never needed it.
     * </p>
     */
    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MINI.deserialize(LegacyCodes.toMiniMessage(input)).decorationIfAbsent(TextDecoration.ITALIC,
                TextDecoration.State.FALSE);
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
     * Serializes a component for a Bukkit legacy-string API without discarding RGB colours.
     */
    public static String legacy(Component component) {
        return component == null ? "" : LEGACY_SECTION_RGB.serialize(component);
    }

    /**
     * Strips all formatting and returns the plain text content of a parsed MiniMessage string.
     */
    public static String strip(String input) {
        return input == null ? "" : PLAIN.serialize(MINI.deserialize(LegacyCodes.toMiniMessage(input)));
    }
}
