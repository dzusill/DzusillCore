package me.dzusill.core.config;

import org.bukkit.plugin.Plugin;

/**
 * Typed view over {@code config.yml}. Demonstrates the recommended pattern for the framework:
 * every config key is referenced exactly once, here, behind a descriptive getter.
 */
public final class SettingsConfig extends AbstractConfig {

    public SettingsConfig(Plugin plugin) {
        super(plugin, "config.yml");
    }

    /**
     * @return the message prefix in MiniMessage format
     */
    public String prefix() {
        return raw().getString("prefix", "<gray>[<aqua>Core</aqua>]</gray> ");
    }

    /**
     * @return whether verbose debug logging is enabled
     */
    public boolean debug() {
        return raw().getBoolean("debug", false);
    }
}
