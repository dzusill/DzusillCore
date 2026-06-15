package me.dzusill.core.database;

import java.util.Map;

/**
 * Immutable connection settings for a SQL database, typically built from {@code database.yml}.
 * The {@code properties} map carries driver-specific JDBC options (e.g. {@code useSSL}).
 *
 * @param host                 server host
 * @param port                 server port
 * @param database             schema/database name
 * @param username             login user
 * @param password             login password
 * @param maximumPoolSize      maximum number of pooled connections
 * @param connectionTimeoutMs  how long to wait for a connection before failing
 * @param properties           extra JDBC connection properties
 */
public record DatabaseCredentials(
        String host,
        int port,
        String database,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMs,
        Map<String, String> properties) {

    public DatabaseCredentials {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
