package me.dzusill.core.database;

import me.dzusill.core.database.query.RowMapper;
import me.dzusill.core.database.query.SqlFunction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous, dialect-agnostic access to a SQL database. Every method returns a
 * {@link CompletableFuture} executed off the main server thread; callers resume on the main thread
 * (when touching the Bukkit API) using an appropriate executor, e.g.
 * {@code SchedulerService#mainThreadExecutor()}.
 *
 * <p>Implementations are backed by a connection pool. This interface is intentionally free of any
 * Bukkit dependency so it can be unit-tested against an in-memory database.</p>
 */
public interface Database extends AutoCloseable {

    /**
     * @return the backend type, exposing dialect helpers such as upsert syntax
     */
    DatabaseType type();

    /**
     * Borrows a raw connection from the pool. The caller is responsible for closing it (preferably
     * with try-with-resources). Prefer the higher-level query methods where possible.
     */
    Connection connection() throws SQLException;

    /**
     * Runs a query expected to return at most one row.
     */
    <T> CompletableFuture<Optional<T>> queryOne(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Runs a query returning any number of rows.
     */
    <T> CompletableFuture<List<T>> queryList(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Runs an {@code INSERT}/{@code UPDATE}/{@code DELETE} or DDL statement.
     *
     * @return the affected row count
     */
    CompletableFuture<Integer> update(String sql, Object... params);

    /**
     * Runs the same statement once per parameter row as a single batch.
     *
     * @return the per-statement affected counts
     */
    CompletableFuture<int[]> batch(String sql, List<Object[]> parameterRows);

    /**
     * Executes arbitrary work against a borrowed connection (e.g. a multi-statement transaction).
     * The connection is closed automatically after the work completes.
     */
    <T> CompletableFuture<T> withConnection(SqlFunction<T> work);

    /**
     * Closes the underlying connection pool.
     */
    @Override
    void close();
}
