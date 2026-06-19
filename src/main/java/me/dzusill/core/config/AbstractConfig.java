package me.dzusill.core.config;

import org.bukkit.plugin.Plugin;

/**
 * Base class for strongly-typed config wrappers. Instead of scattering raw string keys such as
 * {@code cfg.getString("a.b.c")} across the codebase, subclasses expose intent-revealing getters backed by a single
 * {@link Config} instance, centralizing the key strings and default handling.
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * public final class SettingsConfig extends AbstractConfig {
 *     public SettingsConfig(Plugin plugin) {
 *         super(plugin, "config.yml");
 *     }
 *
 *     public String prefix() {
 *         return raw().getString("prefix", "<gray>[Core]</gray> ");
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractConfig {

    private final Plugin plugin;
    private final String resourcePath;
    private final String serverPath;
    private final String[] ignoredSections;

    private Config config;

    /**
     * @param plugin
     *            owning plugin
     * @param fileName
     *            name used both as the bundled resource and the on-disk file
     */
    protected AbstractConfig(Plugin plugin, String fileName) {
        this(plugin, fileName, fileName);
    }

    protected AbstractConfig(Plugin plugin, String resourcePath, String serverPath, String... ignoredSections) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.serverPath = serverPath;
        this.ignoredSections = ignoredSections;
        this.config = Config.loadConfig(plugin, resourcePath, serverPath, ignoredSections);
    }

    /**
     * @return the underlying commented config for direct key access
     */
    public Config raw() {
        return config;
    }

    /**
     * Reloads the underlying file from disk.
     */
    public void reload() {
        this.config = Config.loadConfig(plugin, resourcePath, serverPath, ignoredSections);
    }

    /**
     * Persists any programmatic changes back to disk.
     */
    public void save() {
        config.save();
    }
}
