package me.dzusill.core.database;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import me.dzusill.core.config.AbstractConfig;

/**
 * Typed view over {@code database.yml}. Keeps connection settings (and their defaults) in one place and produces the
 * {@link DatabaseCredentials} consumed by the {@link DatabaseManager}.
 */
public final class DatabaseConfig extends AbstractConfig {

    /**
     * Suffixes H2 appends itself. Stripping them keeps a configured {@code data.mv.db} from becoming
     * {@code data.mv.db.mv.db} on disk.
     */
    private static final String[] H2_FILE_SUFFIXES = {".mv.db", ".h2.db"};

    private final Plugin plugin;

    public DatabaseConfig(Plugin plugin) {
        super(plugin, "database.yml");
        this.plugin = plugin;
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
        return DatabaseType.fromString(raw().getString("type", "H2"));
    }

    /**
     * @return connection credentials assembled from the file
     */
    public DatabaseCredentials credentials() {
        DatabaseType type = type();
        return new DatabaseCredentials(raw().getString("host", "localhost"), raw().getInt("port", type.defaultPort()),
                raw().getString("database", "minecraft"), raw().getString("username", "root"),
                raw().getString("password", ""), raw().getInt("pool.maximum-pool-size", 10),
                raw().getLong("pool.connection-timeout-ms", 30000L), readProperties(), resolveFile());
    }

    /**
     * Absolute path of the embedded database file. A relative {@code file} is resolved against the plugin's data
     * folder, so the default config puts the database next to the plugin's other files rather than in the server root.
     */
    private String resolveFile() {
        String configured = raw().getString("file", "data");
        String lower = configured.toLowerCase(Locale.ROOT);
        for (String suffix : H2_FILE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                configured = configured.substring(0, configured.length() - suffix.length());
                break;
            }
        }
        File file = new File(configured);
        return (file.isAbsolute() ? file : new File(plugin.getDataFolder(), configured)).getAbsolutePath();
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
