# Database — Queries

The `Database` interface provides five async query methods, a raw connection accessor, and a dialect accessor. All methods return `CompletableFuture<T>` executed off the main thread.

## Database API

```java
public interface Database extends AutoCloseable {
    DatabaseType type();
    Connection connection() throws SQLException;

    <T> CompletableFuture<Optional<T>> queryOne(String sql, RowMapper<T> mapper, Object... params);
    <T> CompletableFuture<List<T>>    queryList(String sql, RowMapper<T> mapper, Object... params);
    CompletableFuture<Integer>         update(String sql, Object... params);
    CompletableFuture<int[]>           batch(String sql, List<Object[]> parameterRows);
    <T> CompletableFuture<T>           withConnection(SqlFunction<T> work);
    void close();
}
```

## queryOne — single-row queries

```java
db.queryOne(
        "SELECT coins FROM players WHERE uuid = ?",
        rs -> rs.getLong("coins"),
        player.getUniqueId().toString())
  .thenAcceptAsync(
        opt -> opt.ifPresent(coins -> updateHud(player, coins)),
        scheduler.mainThreadExecutor());
```

Returns `Optional.empty()` if the result set is empty.

## queryList — multi-row queries

```java
db.queryList(
        "SELECT uuid, name, coins FROM players ORDER BY coins DESC LIMIT 10",
        rs -> new PlayerRecord(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getLong("coins"),
                rs.getLong("last_seen")))
  .thenAcceptAsync(records -> showLeaderboard(player, records), scheduler.mainThreadExecutor());
```

## update — INSERT / UPDATE / DELETE / DDL

```java
db.update(
        "UPDATE players SET coins = coins + ? WHERE uuid = ?",
        amount,
        player.getUniqueId().toString())
  .thenAcceptAsync(rows -> {
      if (rows > 0) player.sendMessage(ColorUtils.parse("<green>Coins added!"));
  }, scheduler.mainThreadExecutor());
```

Returns the affected row count.

## batch — same statement with multiple parameter sets

```java
List<Object[]> rows = players.stream()
        .map(p -> new Object[]{p.getUniqueId().toString(), System.currentTimeMillis()})
        .toList();

db.batch("UPDATE players SET last_seen = ? WHERE uuid = ?", rows)
  .thenRun(() -> plugin.getLogger().info("Batch update done."));
```

## withConnection — multi-statement transactions

```java
db.withConnection(conn -> {
    conn.setAutoCommit(false);
    try (PreparedStatement s1 = conn.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE id = ?");
         PreparedStatement s2 = conn.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
        s1.setDouble(1, amount); s1.setInt(2, fromId); s1.executeUpdate();
        s2.setDouble(1, amount); s2.setInt(2, toId);   s2.executeUpdate();
        conn.commit();
    } catch (SQLException ex) {
        conn.rollback();
        throw ex;
    }
    return null;
});
```

## RowMapper

A `@FunctionalInterface` that maps the current row of a `ResultSet` to a value:

```java
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
```

Use a lambda or a method reference:

```java
RowMapper<String> nameMapper = rs -> rs.getString("name");
RowMapper<PlayerRecord> recordMapper = rs -> new PlayerRecord(
        UUID.fromString(rs.getString("uuid")),
        rs.getString("name"),
        rs.getLong("coins"),
        rs.getLong("last_seen"));
```

## SqlQuery — passing queries around

`SqlQuery` bundles a SQL string and its positional parameters as a value object:

```java
SqlQuery query = SqlQuery.of("SELECT * FROM players WHERE coins > ?", 1000);
db.queryList(query.sql(), mapper, query.parameters());
```

## Statements helpers

```java
Statements.placeholders(3)                    // "?, ?, ?"
Statements.columns(List.of("id","name"))       // "id, name"
Statements.assignments(List.of("a","b"))       // "a = ?, b = ?"
Statements.bind(preparedStatement, v1, v2)     // binds positional params 1-based
```

## Error handling

On failure, futures complete exceptionally with `DatabaseException` (unchecked). Handle it downstream:

```java
db.queryOne(...)
  .thenAccept(opt -> { ... })
  .exceptionally(ex -> {
      plugin.getLogger().severe("DB error: " + ex.getMessage());
      return null;
  });
```
