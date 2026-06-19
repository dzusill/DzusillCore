package me.dzusill.core.database.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import me.dzusill.core.database.Database;
import me.dzusill.core.database.query.RowMapper;
import me.dzusill.core.database.query.Statements;

/**
 * SQL-backed {@link Repository} that turns a handful of table descriptors (provided by subclasses) into full async
 * CRUD. The insert-or-update statement is produced by the database's {@link me.dzusill.core.database.DatabaseType}, so
 * the same repository works on MySQL and PostgreSQL without dialect-specific code.
 *
 * @param <ID>
 *            identifier type
 * @param <T>
 *            entity type
 */
public abstract class AbstractSqlRepository<ID, T> implements Repository<ID, T> {

    protected final Database database;

    protected AbstractSqlRepository(Database database) {
        this.database = database;
    }

    /** @return the table name */
    protected abstract String table();

    /** @return all column names, in the order used by {@link #values(Object)} */
    protected abstract List<String> columns();

    /** @return the subset of {@link #columns()} forming the primary key */
    protected abstract List<String> keyColumns();

    /** @return mapper from a result row to an entity */
    protected abstract RowMapper<T> mapper();

    /** @return column values for the entity, aligned with {@link #columns()} */
    protected abstract Object[] values(T entity);

    /** @return key column values for the id, aligned with {@link #keyColumns()} */
    protected abstract Object[] keyValues(ID id);

    @Override
    public CompletableFuture<Optional<T>> find(ID id) {
        String sql = "SELECT " + Statements.columns(columns()) + " FROM " + table() + " WHERE " + whereKeys();
        return database.queryOne(sql, mapper(), keyValues(id));
    }

    @Override
    public CompletableFuture<List<T>> findAll() {
        String sql = "SELECT " + Statements.columns(columns()) + " FROM " + table();
        return database.queryList(sql, mapper());
    }

    @Override
    public CompletableFuture<Void> save(T entity) {
        String sql = database.type().upsert(table(), columns(), keyColumns());
        return database.update(sql, values(entity)).thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<Void> delete(ID id) {
        String sql = "DELETE FROM " + table() + " WHERE " + whereKeys();
        return database.update(sql, keyValues(id)).thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<Boolean> exists(ID id) {
        String sql = "SELECT 1 FROM " + table() + " WHERE " + whereKeys();
        return database.queryOne(sql, resultSet -> Boolean.TRUE, keyValues(id)).thenApply(Optional::isPresent);
    }

    private String whereKeys() {
        return keyColumns().stream().map(column -> column + " = ?").collect(Collectors.joining(" AND "));
    }
}
