package me.dzusill.core.database;

import java.util.Optional;
import java.util.concurrent.Executor;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.service.Service;

/**
 * Owns the active {@link Database} connection and its lifecycle. Honors the {@code enabled} toggle in
 * {@code database.yml}: when disabled, the plugin runs normally with no database, and any code that needs one fails
 * fast via {@link #database()} (or checks {@link #isEnabled()} first).
 */
public final class DatabaseManager implements Service, AutoCloseable {

    private final CorePlugin plugin;
    private final DatabaseConfig config;
    private final Executor asyncExecutor;

    private Database database;

    public DatabaseManager(CorePlugin plugin, DatabaseConfig config, Executor asyncExecutor) {
        this.plugin = plugin;
        this.config = config;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Connects and applies the schema if the database is enabled in config. Safe to call once during startup; a no-op
     * when disabled.
     */
    public void start() {
        if (!config.enabled()) {
            plugin.getLogger().info("Database is disabled in database.yml; running without it.");
            return;
        }

        DatabaseType type = config.type();
        this.database = create(type, config.credentials());
        SchemaInitializer.initialize(plugin, database);
        plugin.getLogger().info("Connected to " + type + " database.");
    }

    private Database create(DatabaseType type, DatabaseCredentials credentials) {
        return switch (type) {
            case MYSQL -> new MySqlDatabase(credentials, asyncExecutor);
            case POSTGRESQL -> new PostgreSqlDatabase(credentials, asyncExecutor);
        };
    }

    /**
     * @return {@code true} if a database connection is active
     */
    public boolean isEnabled() {
        return database != null;
    }

    /**
     * @return the active database
     * @throws DatabaseException
     *             if the database is disabled
     */
    public Database database() {
        if (database == null) {
            throw new DatabaseException("Database is disabled; enable it in database.yml");
        }
        return database;
    }

    /**
     * @return the active database if enabled, otherwise empty
     */
    public Optional<Database> optional() {
        return Optional.ofNullable(database);
    }

    @Override
    public void close() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
