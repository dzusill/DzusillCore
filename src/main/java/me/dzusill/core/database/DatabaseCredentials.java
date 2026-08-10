package me.dzusill.core.database;

import java.util.Map;

/**
 * Immutable connection settings for a SQL database, typically built from {@code database.yml}. The {@code properties}
 * map carries driver-specific JDBC options (e.g. {@code useSSL}).
 *
 * <p>
 * Server-backed types ({@link DatabaseType#MYSQL}, {@link DatabaseType#POSTGRESQL}) use host/port/database; embedded
 * types ({@link DatabaseType#H2}) use {@code file} instead and ignore host and port.
 * </p>
 *
 * @param host
 *            server host, unused by embedded types
 * @param port
 *            server port, unused by embedded types
 * @param database
 *            schema/database name
 * @param username
 *            login user
 * @param password
 *            login password
 * @param maximumPoolSize
 *            maximum number of pooled connections
 * @param connectionTimeoutMs
 *            how long to wait for a connection before failing
 * @param properties
 *            extra JDBC connection properties
 * @param file
 *            database file path for embedded types, {@code null} for server-backed ones
 */
public record DatabaseCredentials(String host, int port, String database, String username, String password,
        int maximumPoolSize, long connectionTimeoutMs, Map<String, String> properties, String file) {

    public DatabaseCredentials {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    /**
     * Convenience form for server-backed types, leaving the embedded file path unset. Kept so credentials built before
     * embedded support existed still compile.
     */
    public DatabaseCredentials(String host, int port, String database, String username, String password,
            int maximumPoolSize, long connectionTimeoutMs, Map<String, String> properties) {
        this(host, port, database, username, password, maximumPoolSize, connectionTimeoutMs, properties, null);
    }
}
