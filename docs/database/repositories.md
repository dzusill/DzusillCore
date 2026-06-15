# Database — Repositories

The repository layer provides a clean, intention-revealing CRUD API on top of the raw `Database`. It auto-generates dialect-correct SQL (including upsert) from a handful of declarative descriptors.

## Repository interface

```java
public interface Repository<ID, T> {
    CompletableFuture<Optional<T>> find(ID id);
    CompletableFuture<List<T>>     findAll();
    CompletableFuture<Void>        save(T entity);
    CompletableFuture<Void>        delete(ID id);
    CompletableFuture<Boolean>     exists(ID id);
}
```

## AbstractSqlRepository

Extend `AbstractSqlRepository<ID, T>` and implement five descriptors. The base class generates all SQL statements automatically:

| Method | Returns | Used for |
|---|---|---|
| `table()` | `String` | table name |
| `columns()` | `List<String>` | all column names in order |
| `keyColumns()` | `List<String>` | primary-key subset of columns |
| `mapper()` | `RowMapper<T>` | ResultSet row → entity |
| `values(T)` | `Object[]` | entity → column values (aligned with `columns()`) |
| `keyValues(ID)` | `Object[]` | id → key column values (aligned with `keyColumns()`) |

## PlayerRepository — full example

Entity:

```java
public record PlayerRecord(UUID uuid, String name, long coins, long lastSeen) {}
```

Repository:

```java
public final class PlayerRepository extends AbstractSqlRepository<UUID, PlayerRecord> {

    public PlayerRepository(Database database) {
        super(database);
    }

    @Override protected String table()      { return "core_players"; }
    @Override protected List<String> columns()    { return List.of("uuid","name","coins","last_seen"); }
    @Override protected List<String> keyColumns() { return List.of("uuid"); }

    @Override
    protected RowMapper<PlayerRecord> mapper() {
        return rs -> new PlayerRecord(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getLong("coins"),
                rs.getLong("last_seen"));
    }

    @Override
    protected Object[] values(PlayerRecord e) {
        return new Object[]{e.uuid().toString(), e.name(), e.coins(), e.lastSeen()};
    }

    @Override
    protected Object[] keyValues(UUID id) {
        return new Object[]{id.toString()};
    }
}
```

Usage:

```java
PlayerRepository repo = new PlayerRepository(db.database());

// Find a player
repo.find(player.getUniqueId())
    .thenAcceptAsync(opt -> opt.ifPresent(rec -> showProfile(player, rec)),
                     scheduler.mainThreadExecutor());

// Save (insert or update — dialect-aware)
repo.save(new PlayerRecord(uuid, name, coins, System.currentTimeMillis()));

// Delete
repo.delete(player.getUniqueId());

// Exists check
repo.exists(uuid).thenAccept(exists -> { ... });

// All rows
repo.findAll().thenAccept(records -> { ... });
```

## SQL generated (MySQL example)

| Method | SQL |
|---|---|
| `find(uuid)` | `SELECT uuid, name, coins, last_seen FROM core_players WHERE uuid = ?` |
| `findAll()` | `SELECT uuid, name, coins, last_seen FROM core_players` |
| `save(entity)` | `INSERT INTO core_players (uuid, name, coins, last_seen) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), ...` |
| `delete(uuid)` | `DELETE FROM core_players WHERE uuid = ?` |
| `exists(uuid)` | `SELECT 1 FROM core_players WHERE uuid = ?` |

On PostgreSQL, `save()` uses `ON CONFLICT (uuid) DO UPDATE SET ...` automatically.

## SqlDataStore — storage-agnostic bridge

`SqlDataStore<V>` implements the same `DataStore<String, V>` interface as `YamlDataStore`, backed by a two-column table. Use it when you want SQL persistence but want feature code to remain storage-agnostic:

```java
SqlDataStore<String> store = new SqlDataStore<>(
        database,
        "kv_store",     // table name
        "k",            // key column
        "v",            // value column
        value -> value,                  // serializer
        raw -> raw.toString());           // deserializer

store.load();
store.put("server-name", "SurvivalMC");
store.save();

Optional<String> name = store.get("server-name");
```

This lets you later swap from `YamlDataStore` to `SqlDataStore` without touching the rest of your code.
