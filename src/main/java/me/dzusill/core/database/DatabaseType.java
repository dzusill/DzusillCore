package me.dzusill.core.database;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.dzusill.core.database.query.Statements;

/**
 * Enumerates the supported SQL backends and encapsulates everything that differs between them: the JDBC driver class,
 * the URL format, the default port and the dialect-specific "upsert" statement. New backends are added by introducing a
 * new constant, keeping the rest of the database layer untouched.
 */
public enum DatabaseType {

    MYSQL("com.mysql.cj.jdbc.Driver", 3306) {
        @Override
        public String jdbcUrl(DatabaseCredentials credentials) {
            return "jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/" + credentials.database();
        }

        @Override
        public String upsert(String table, List<String> columns, List<String> keyColumns) {
            String updates = columns.stream().filter(column -> !keyColumns.contains(column))
                    .map(column -> column + " = VALUES(" + column + ")").collect(Collectors.joining(", "));
            return "INSERT INTO " + table + " (" + Statements.columns(columns) + ") VALUES ("
                    + Statements.placeholders(columns.size()) + ") ON DUPLICATE KEY UPDATE " + updates;
        }
    },

    POSTGRESQL("org.postgresql.Driver", 5432) {
        @Override
        public String jdbcUrl(DatabaseCredentials credentials) {
            return "jdbc:postgresql://" + credentials.host() + ":" + credentials.port() + "/" + credentials.database();
        }

        @Override
        public String upsert(String table, List<String> columns, List<String> keyColumns) {
            String updates = columns.stream().filter(column -> !keyColumns.contains(column))
                    .map(column -> column + " = EXCLUDED." + column).collect(Collectors.joining(", "));
            return "INSERT INTO " + table + " (" + Statements.columns(columns) + ") VALUES ("
                    + Statements.placeholders(columns.size()) + ") ON CONFLICT (" + Statements.columns(keyColumns)
                    + ") DO UPDATE SET " + updates;
        }
    },

    /**
     * Embedded, file-backed H2 running in MySQL compatibility mode. Needs no server, no credentials and no install step
     * - the whole database is one file under the plugin's data folder - which makes it the sensible default for servers
     * that don't already run MySQL. {@link DatabaseCredentials#file()} carries the path; host and port are unused.
     */
    H2("org.h2.Driver", 0) {
        @Override
        public String jdbcUrl(DatabaseCredentials credentials) {
            return "jdbc:h2:file:" + embeddedPath(credentials) + H2_OPTIONS;
        }

        /**
         * H2's own MERGE form rather than the MySQL {@code ON DUPLICATE KEY} syntax: it is understood in every
         * compatibility mode, so the statement keeps working even if {@link #H2_OPTIONS} changes.
         */
        @Override
        public String upsert(String table, List<String> columns, List<String> keyColumns) {
            return "MERGE INTO " + table + " (" + Statements.columns(columns) + ") KEY ("
                    + Statements.columns(keyColumns) + ") VALUES (" + Statements.placeholders(columns.size()) + ")";
        }

        /**
         * H2 rejects unknown connection settings outright, so the MySQL-oriented defaults shipped in
         * {@code database.yml} ({@code useSSL}, {@code characterEncoding}) would fail every connection. Embedded H2
         * needs none of them; everything it does need is already in the URL.
         */
        @Override
        public Map<String, String> connectionProperties(DatabaseCredentials credentials) {
            return Map.of();
        }
    };

    /**
     * MySQL dialect plus lower-cased unquoted identifiers, so a schema file written for MySQL applies unchanged. Both
     * are creation-time settings in H2 and are read back from the file afterwards, so they must stay identical across
     * every connection to the same database - do not make them configurable per connection.
     */
    private static final String H2_OPTIONS = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";

    private final String driverClassName;
    private final int defaultPort;

    DatabaseType(String driverClassName, int defaultPort) {
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
    }

    /**
     * Builds the JDBC connection URL from the given credentials.
     */
    public abstract String jdbcUrl(DatabaseCredentials credentials);

    /**
     * Builds an insert-or-update statement for the dialect.
     *
     * @param table
     *            target table
     * @param columns
     *            all inserted columns, in order
     * @param keyColumns
     *            the subset of {@code columns} forming the conflict/primary key
     */
    public abstract String upsert(String table, List<String> columns, List<String> keyColumns);

    /**
     * JDBC connection properties actually handed to the driver. Defaults to whatever the config carries; backends whose
     * driver refuses unknown settings override it.
     */
    public Map<String, String> connectionProperties(DatabaseCredentials credentials) {
        return credentials.properties();
    }

    public String driverClassName() {
        return driverClassName;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /**
     * Path of an embedded database file. Falls back to the plain database name so a hand-built
     * {@link DatabaseCredentials} without a file still resolves to something usable (relative to the server's working
     * directory); {@code DatabaseConfig} always supplies an absolute path.
     */
    private static String embeddedPath(DatabaseCredentials credentials) {
        String file = credentials.file();
        return file == null || file.isBlank() ? credentials.database() : file;
    }

    /**
     * Resolves a type from its config name, case-insensitively.
     *
     * @throws DatabaseException
     *             if the name is not a known type
     */
    public static DatabaseType fromString(String name) {
        for (DatabaseType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new DatabaseException("Unknown database type: " + name);
    }
}
