package me.dzusill.core.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Integration with PlaceholderAPI. When active, resolves placeholders in arbitrary strings so the
 * plugin can support third-party placeholders in its messages.
 */
public final class PlaceholderApiHook extends PluginHook {

    public PlaceholderApiHook() {
        super("PlaceholderAPI");
    }

    @Override
    protected void setup() {
        // Presence is sufficient; custom expansions are registered separately by the plugin.
    }

    /**
     * Resolves PlaceholderAPI placeholders in {@code text} for the given player.
     */
    public String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
