package me.dzusill.core.database.repository;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import me.dzusill.core.database.Database;
import me.dzusill.core.storage.AbstractDataStore;

/**
 * Bridges the SQL layer to the framework's {@link me.dzusill.core.storage.DataStore} abstraction: a two-column
 * ({@code key}, {@code value}) table exposed through the same cached, load/save API as
 * {@link me.dzusill.core.storage.YamlDataStore}. This lets feature code stay storage-agnostic and swap YAML for SQL
 * without changes.
 *
 * <p>
 * Like the YAML store, this caches in memory; {@link #load()} and {@link #save()} synchronize with the database
 * (blocking briefly, intended for startup/shutdown or explicit flushes).
 * </p>
 *
 * @param <V>
 *            value type
 */
public final class SqlDataStore<V> extends AbstractDataStore<String, V> {

    private final Database database;
    private final String table;
    private final String keyColumn;
    private final String valueColumn;
    private final Function<V, Object> serializer;
    private final Function<Object, V> deserializer;

    public SqlDataStore(Database database, String table, String keyColumn, String valueColumn,
            Function<V, Object> serializer, Function<Object, V> deserializer) {
        this.database = database;
        this.table = table;
        this.keyColumn = keyColumn;
        this.valueColumn = valueColumn;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    @Override
    public void load() {
        cache.clear();
        String sql = "SELECT " + keyColumn + ", " + valueColumn + " FROM " + table;
        List<Map.Entry<String, V>> rows = database
                .queryList(sql,
                        resultSet -> (Map.Entry<String, V>) new AbstractMap.SimpleEntry<>(
                                resultSet.getString(keyColumn), deserializer.apply(resultSet.getObject(valueColumn))))
                .join();
        for (Map.Entry<String, V> row : rows) {
            cache.put(row.getKey(), row.getValue());
        }
    }

    @Override
    public void save() {
        if (cache.isEmpty()) {
            return;
        }
        String sql = database.type().upsert(table, List.of(keyColumn, valueColumn), List.of(keyColumn));
        List<Object[]> rows = new ArrayList<>();
        cache.forEach((key, value) -> rows.add(new Object[]{key, serializer.apply(value)}));
        database.batch(sql, rows).join();
    }
}
