package me.dzusill.core.database;

import me.dzusill.core.config.AbstractConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed view over {@code database.yml}. Keeps connection settings (and their defaults) in one
 * place and produces the {@link DatabaseCredentials} consumed by the {@link DatabaseManager}.
 */
public final class DatabaseConfig extends AbstractConfig {

    public DatabaseConfig(Plugin plugin) {
        super(plugin, "database.yml");
    }

    /**
     * @return whether the database subsystem should be started at all
     */
    public boolean enabled() {
        return raw().getBoolean("enabled", false);
    }

    /**
     * @return the configured backend type
     */
    public DatabaseType type() {
        return DatabaseType.fromString(raw().getString("type", "MYSQL"));
    }

    /**
     * @return connection credentials assembled from the file
     */
    public DatabaseCredentials credentials() {
        DatabaseType type = type();
        return new DatabaseCredentials(
                raw().getString("host", "localhost"),
                raw().getInt("port", type.defaultPort()),
                raw().getString("database", "minecraft"),
                raw().getString("username", "root"),
                raw().getString("password", ""),
                raw().getInt("pool.maximum-pool-size", 10),
                raw().getLong("pool.connection-timeout-ms", 30000L),
                readProperties());
    }

    private Map<String, String> readProperties() {
        Map<String, String> properties = new HashMap<>();
        ConfigurationSection section = raw().getConfigurationSection("properties");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                properties.put(key, section.getString(key));
            }
        }
        return properties;
    }
}
