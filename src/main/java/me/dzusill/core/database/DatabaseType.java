package me.dzusill.core.database;

import me.dzusill.core.database.query.Statements;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enumerates the supported SQL backends and encapsulates everything that differs between them:
 * the JDBC driver class, the URL format, the default port and the dialect-specific "upsert"
 * statement. New backends (e.g. SQLite) are added by introducing a new constant, keeping the rest
 * of the database layer untouched.
 */
public enum DatabaseType {

    MYSQL("com.mysql.cj.jdbc.Driver", 3306) {
        @Override
        public String jdbcUrl(DatabaseCredentials credentials) {
            return "jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/" + credentials.database();
        }

        @Override
        public String upsert(String table, List<String> columns, List<String> keyColumns) {
            String updates = columns.stream()
                    .filter(column -> !keyColumns.contains(column))
                    .map(column -> column + " = VALUES(" + column + ")")
                    .collect(Collectors.joining(", "));
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
            String updates = columns.stream()
                    .filter(column -> !keyColumns.contains(column))
                    .map(column -> column + " = EXCLUDED." + column)
                    .collect(Collectors.joining(", "));
            return "INSERT INTO " + table + " (" + Statements.columns(columns) + ") VALUES ("
                    + Statements.placeholders(columns.size()) + ") ON CONFLICT ("
                    + Statements.columns(keyColumns) + ") DO UPDATE SET " + updates;
        }
    };

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
     * @param table      target table
     * @param columns    all inserted columns, in order
     * @param keyColumns the subset of {@code columns} forming the conflict/primary key
     */
    public abstract String upsert(String table, List<String> columns, List<String> keyColumns);

    public String driverClassName() {
        return driverClassName;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /**
     * Resolves a type from its config name, case-insensitively.
     *
     * @throws DatabaseException if the name is not a known type
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
