package me.dzusill.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.dzusill.core.database.query.RowMapper;
import me.dzusill.core.database.query.SqlFunction;
import me.dzusill.core.database.query.Statements;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Base {@link Database} implementation backed by a HikariCP pool. Subclasses only declare which
 * {@link DatabaseType} they are; all pooling, async wrapping and statement execution live here, so
 * MySQL/PostgreSQL (and future backends) differ by configuration alone.
 *
 * <p>Async behaviour is provided by a supplied {@link Executor}; this class never references the
 * Bukkit API, which keeps it unit-testable against an embedded database.</p>
 */
public abstract class AbstractSqlDatabase implements Database {

    private final DatabaseType type;
    private final HikariDataSource dataSource;
    private final Executor asyncExecutor;

    protected AbstractSqlDatabase(DatabaseType type, DatabaseCredentials credentials, Executor asyncExecutor) {
        this(type, buildDataSource(type, credentials), asyncExecutor);
    }

    /**
     * Test/advanced constructor accepting a pre-built data source.
     */
    protected AbstractSqlDatabase(DatabaseType type, HikariDataSource dataSource, Executor asyncExecutor) {
        this.type = type;
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
    }

    private static HikariDataSource buildDataSource(DatabaseType type, DatabaseCredentials credentials) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("DzusillCore-" + type.name());
        config.setJdbcUrl(type.jdbcUrl(credentials));
        config.setUsername(credentials.username());
        config.setPassword(credentials.password());
        // Load the driver explicitly so a relocated/shaded jar never relies on the JDBC SPI.
        config.setDriverClassName(type.driverClassName());
        config.setMaximumPoolSize(credentials.maximumPoolSize());
        config.setConnectionTimeout(credentials.connectionTimeoutMs());
        // Skip HikariCP's startup validation connection so new HikariDataSource() returns
        // immediately without blocking the main thread on a remote TCP handshake. Connections
        // are opened lazily on first use (always on the async executor, never on main thread).
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(0);
        for (Map.Entry<String, String> property : credentials.properties().entrySet()) {
            config.addDataSourceProperty(property.getKey(), property.getValue());
        }
        return new HikariDataSource(config);
    }

    @Override
    public final DatabaseType type() {
        return type;
    }

    @Override
    public final Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public final <T> CompletableFuture<Optional<T>> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return supplyAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Statements.bind(statement, params);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.ofNullable(mapper.map(resultSet)) : Optional.<T>empty();
                }
            }
        }, "queryOne failed");
    }

    @Override
    public final <T> CompletableFuture<List<T>> queryList(String sql, RowMapper<T> mapper, Object... params) {
        return supplyAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Statements.bind(statement, params);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<T> results = new ArrayList<>();
                    while (resultSet.next()) {
                        results.add(mapper.map(resultSet));
                    }
                    return results;
                }
            }
        }, "queryList failed");
    }

    @Override
    public final CompletableFuture<Integer> update(String sql, Object... params) {
        return supplyAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Statements.bind(statement, params);
                return statement.executeUpdate();
            }
        }, "update failed");
    }

    @Override
    public final CompletableFuture<int[]> batch(String sql, List<Object[]> parameterRows) {
        return supplyAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Object[] row : parameterRows) {
                    Statements.bind(statement, row);
                    statement.addBatch();
                }
                return statement.executeBatch();
            }
        }, "batch failed");
    }

    @Override
    public final <T> CompletableFuture<T> withConnection(SqlFunction<T> work) {
        return supplyAsync(() -> {
            try (Connection connection = connection()) {
                return work.apply(connection);
            }
        }, "withConnection failed");
    }

    @Override
    public final void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier, String errorMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException ex) {
                throw new DatabaseException(errorMessage + ": " + ex.getMessage(), ex);
            }
        }, asyncExecutor);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
