# Database — Configuration

## database.yml

```yaml
# Master switch. Plugin runs without a database when false.
enabled: false

# Backend type: MYSQL or POSTGRESQL
type: MYSQL

host: localhost
port: 3306
database: minecraft
username: root
password: ""

pool:
  maximum-pool-size: 10
  connection-timeout-ms: 30000

# Extra JDBC driver properties (optional)
properties:
  useSSL: "false"
  characterEncoding: "utf8"
```

> Keep `database.yml` out of version control when it contains real credentials. Add it to `.gitignore`.

## DatabaseConfig

`DatabaseConfig` is a typed `AbstractConfig` wrapper over `database.yml`. It reads all connection settings and assembles a `DatabaseCredentials` record:

```java
DatabaseConfig config = new DatabaseConfig(plugin);

boolean enabled     = config.enabled();
DatabaseType type   = config.type();           // MYSQL or POSTGRESQL
DatabaseCredentials creds = config.credentials();
```

`DatabaseCredentials` is an immutable record:

```java
record DatabaseCredentials(
        String host,
        int port,
        String database,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMs,
        Map<String, String> properties)
```

## DatabaseManager

`DatabaseManager` (a `Service`) wraps everything:

```java
// Construction (in DatabaseModule.onEnable):
SchedulerService scheduler = service(SchedulerService.class);
DatabaseManager dbManager = new DatabaseManager(plugin, new DatabaseConfig(plugin), scheduler.asyncExecutor());
dbManager.start();    // connects + applies schema if enabled
provide(DatabaseManager.class, dbManager);

// Usage anywhere else:
DatabaseManager db = service(DatabaseManager.class);
Database database  = db.database();            // throws if disabled
Optional<Database> safe = db.optional();        // safe access
boolean active     = db.isEnabled();
```

`DatabaseModule.onDisable()` calls `dbManager.close()` to shut down the HikariCP pool gracefully.

## Choosing pool size

| Server size | Recommended `maximum-pool-size` |
|---|---|
| Development / single-node small server | 5 |
| Medium server (< 100 concurrent players) | 10 |
| Large server or shared DB | 20+ |

HikariCP recommends: `pool size = (number of CPU cores * 2) + disk spindles`.
