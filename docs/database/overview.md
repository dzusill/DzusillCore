# Database — Overview

DzusillCore provides a fully async, HikariCP-pooled SQL layer supporting **MySQL** and **PostgreSQL**. It is entirely optional: when disabled via config, the plugin runs normally without a database.

## Architecture

```
DatabaseManager (Service)
  └─ Database (interface)
       ├─ MySqlDatabase       ← AbstractSqlDatabase
       └─ PostgreSqlDatabase  ← AbstractSqlDatabase
            └─ HikariCP connection pool
                 └─ queryOne / queryList / update / batch / withConnection
                       └─ RowMapper<T>
                            └─ CompletableFuture<T>  (async, off main thread)

Repository<ID,T>
  └─ AbstractSqlRepository
       └─ Database (uses dialect-aware upsert)

DataStore<String,V>
  └─ SqlDataStore   (bridges DataStore interface to SQL table)
```

## Threading model

Every `Database` method returns a `CompletableFuture<T>` that runs on an off-main-thread executor (provided by `SchedulerService.asyncExecutor()`). **Never** block the main thread waiting for a database call.

To resume on the main thread (for Bukkit API calls), chain with `thenAcceptAsync`:

```java
db.queryOne("SELECT coins FROM players WHERE uuid = ?",
            rs -> rs.getLong("coins"),
            player.getUniqueId().toString())
  .thenAcceptAsync(
      opt -> opt.ifPresent(coins -> player.sendMessage(
              ColorUtils.parse("<gold>Coins: " + coins))),
      scheduler.mainThreadExecutor());   // back on main thread
```

## The enabled toggle

The database is disabled by default. Enable it in `database.yml`:

```yaml
enabled: true
```

Code that optionally uses the database should check `isEnabled()`:

```java
DatabaseManager db = services.get(DatabaseManager.class);
if (db.isEnabled()) {
    db.database().update("INSERT INTO log ...", ...).join();
}
```

Or use `optional()`:

```java
db.optional().ifPresent(database -> {
    database.queryList(...).thenAccept(...);
});
```

## What is shaded

The following are bundled inside the JAR:

| Library | Location in JAR |
|---|---|
| HikariCP | `me.dzusill.core.lib.hikari` (relocated) |
| MySQL Connector/J | `com.mysql` (not relocated — loaded by explicit driver class name) |
| PostgreSQL JDBC | `org.postgresql` (not relocated) |

Relocation prevents conflicts when other plugins on the same server also shade HikariCP.
