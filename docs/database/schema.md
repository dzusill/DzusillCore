# Database — Schema

`SchemaInitializer` automatically applies a bundled SQL file to the database at startup, ensuring required tables exist before the plugin uses them.

## How it works

On `DatabaseManager.start()`, `SchemaInitializer.initialize(plugin, database)` is called. It:

1. Looks for a resource named `schema-<type>.sql` (e.g. `schema-mysql.sql` or `schema-postgresql.sql`).
2. Reads the file, strips comment lines (`-- ...`), and splits on `;`.
3. Executes each statement via `database.update(...).join()`.

If the resource file is not present, initialization is skipped with a log message.

## Bundled schema files

Create per-dialect schema files in `src/main/resources/`:

### schema-mysql.sql

```sql
-- DzusillCore example schema (MySQL dialect)

CREATE TABLE IF NOT EXISTS core_players (
    uuid       VARCHAR(36) NOT NULL,
    name       VARCHAR(16) NOT NULL,
    coins      BIGINT      NOT NULL DEFAULT 0,
    last_seen  BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid)
);
```

### schema-postgresql.sql

```sql
-- DzusillCore example schema (PostgreSQL dialect)

CREATE TABLE IF NOT EXISTS core_players (
    uuid       VARCHAR(36) NOT NULL,
    name       VARCHAR(16) NOT NULL,
    coins      BIGINT      NOT NULL DEFAULT 0,
    last_seen  BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid)
);
```

## Rules for schema files

- Statements are separated by `;`.
- Lines starting with `--` are treated as comments and skipped.
- Use `CREATE TABLE IF NOT EXISTS` so re-running on an already-initialized database is safe.
- Keep the two files in sync unless a dialect genuinely requires different syntax (e.g. `AUTO_INCREMENT` vs `SERIAL`).

## Multiple tables

```sql
CREATE TABLE IF NOT EXISTS economy (
    uuid    VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(20,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS warps (
    name      VARCHAR(64) PRIMARY KEY,
    world     VARCHAR(64) NOT NULL,
    x         DOUBLE NOT NULL,
    y         DOUBLE NOT NULL,
    z         DOUBLE NOT NULL,
    yaw       FLOAT NOT NULL DEFAULT 0,
    pitch     FLOAT NOT NULL DEFAULT 0
);
```

## Schema versioning / migrations

`SchemaInitializer` is intentionally minimal. For versioned migrations (V1__init.sql, V2__add_column.sql) implement your own migration runner using `withConnection()` and a `schema_version` table, or integrate a library such as Flyway or Liquibase.
