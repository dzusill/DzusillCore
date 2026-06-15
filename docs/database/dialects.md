# Database — Dialects

DzusillCore supports **MySQL** and **PostgreSQL** out of the box. All dialect-specific differences (JDBC driver class, URL format, default port, upsert syntax) are encapsulated in the `DatabaseType` enum. Adding a new backend is a single enum constant plus a thin subclass of `AbstractSqlDatabase`.

## DatabaseType enum

```java
public enum DatabaseType {
    MYSQL    ("com.mysql.cj.jdbc.Driver", 3306),
    POSTGRESQL("org.postgresql.Driver",  5432);

    public String jdbcUrl(DatabaseCredentials credentials) { ... }
    public String upsert(String table, List<String> columns, List<String> keyColumns) { ... }
    public String driverClassName() { ... }
    public int    defaultPort()     { ... }

    public static DatabaseType fromString(String name)   { ... }  // case-insensitive
}
```

## Upsert syntax comparison

| Backend | Syntax produced |
|---|---|
| MySQL | `INSERT INTO t (...) VALUES (?) ON DUPLICATE KEY UPDATE a = VALUES(a), ...` |
| PostgreSQL | `INSERT INTO t (...) VALUES (?) ON CONFLICT (id) DO UPDATE SET a = EXCLUDED.a, ...` |

`AbstractSqlRepository.save()` calls `database.type().upsert(...)` so the same repository implementation works on both backends unchanged.

## Switching backends

Change a single line in `database.yml`:

```yaml
type: POSTGRESQL
port: 5432
```

No Java code changes are required.

## Adding a new backend (e.g. SQLite)

1. Add the JDBC driver to `pom.xml` with `scope compile`.
2. Add a constant to `DatabaseType`:

```java
SQLITE("org.sqlite.JDBC", 0) {
    @Override
    public String jdbcUrl(DatabaseCredentials credentials) {
        return "jdbc:sqlite:" + credentials.database() + ".db";
    }

    @Override
    public String upsert(String table, List<String> columns, List<String> keyColumns) {
        // SQLite supports INSERT OR REPLACE
        return "INSERT OR REPLACE INTO " + table + " (" + Statements.columns(columns)
                + ") VALUES (" + Statements.placeholders(columns.size()) + ")";
    }
}
```

3. Create the driver subclass:

```java
public final class SqliteDatabase extends AbstractSqlDatabase {
    public SqliteDatabase(DatabaseCredentials credentials, Executor asyncExecutor) {
        super(DatabaseType.SQLITE, credentials, asyncExecutor);
    }
}
```

4. Add a case to `DatabaseManager.create()`:

```java
case SQLITE -> new SqliteDatabase(credentials, asyncExecutor);
```

5. Write and bundle `schema-sqlite.sql`. `SchemaInitializer` will pick it up automatically.
