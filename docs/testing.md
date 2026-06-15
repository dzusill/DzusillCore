# Testing

DzusillCore ships with a test suite that covers the framework itself and serves as a reference for testing plugins built on it.

## Test stack

| Tool | Version | Role |
|---|---|---|
| JUnit 5 | 5.11.4 | Test runner and assertions |
| Mockito | 5.14.2 | Mocking Bukkit objects |
| MockBukkit | 3.93.3 | Paper server simulation |
| H2 | 2.3.232 | In-memory SQL database for DB integration tests |

## Running the tests

```bash
mvn test
```

All 27 tests should pass (1 skipped — a known MockBukkit simulation limitation).

## Test types

### Pure unit tests (no server needed)

Test utility classes and plain logic without mocking a Bukkit server:

```java
class CooldownManagerTest {

    @Test
    void notActiveBeforeStart() {
        CooldownManager<UUID> cd = new CooldownManager<>(5, TimeUnit.SECONDS);
        assertFalse(cd.isActive(UUID.randomUUID()));
    }

    @Test
    void activeAfterStart() {
        CooldownManager<String> cd = new CooldownManager<>(5, TimeUnit.SECONDS);
        cd.start("player");
        assertTrue(cd.isActive("player"));
    }
}
```

### MockBukkit integration tests

Tests that need Bukkit API (inventories, items, plugin lifecycle):

```java
class ItemBuilderTest {

    @BeforeEach void setUp()    { MockBukkit.mock(); }
    @AfterEach  void tearDown() { MockBukkit.unmock(); }

    @Test
    void buildsItemWithNameAndLore() {
        ItemStack item = new ItemBuilder(Material.DIAMOND)
                .name("<aqua>Test")
                .lore("<gray>Line 1")
                .build();
        assertEquals(Material.DIAMOND, item.getType());
        assertNotNull(item.getItemMeta().displayName());
    }
}
```

### Plugin lifecycle tests

Load the full plugin through MockBukkit to test modules, commands, and configs end-to-end:

```java
class CommandRegistryTest {

    private ServerMock server;
    private ExamplePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void healCommandRestoresFullHealth() {
        PlayerMock player = server.addPlayer();
        player.setHealth(1.0);
        player.performCommand("heal");
        assertEquals(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), player.getHealth());
    }
}
```

> **Important**: the plugin main class (e.g. `ExamplePlugin`) must **not** be `final`. MockBukkit subclasses the plugin class internally. If it is `final`, MockBukkit throws `IllegalArgumentException`.

### Database integration tests (H2)

Test the full SQL stack against an in-memory H2 database with MySQL compatibility mode — no real server needed:

```java
class DatabaseIntegrationTest {

    private Database database;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        HikariDataSource dataSource = new HikariDataSource(config);
        database = new AbstractSqlDatabase(DatabaseType.MYSQL, dataSource, Runnable::run) {};
    }

    @AfterEach
    void tearDown() { database.close(); }

    @Test
    void repositorySupportsCrudAndUpsert() {
        database.update("CREATE TABLE core_players (uuid VARCHAR(36) PRIMARY KEY, "
                + "name VARCHAR(16), coins BIGINT, last_seen BIGINT)").join();
        PlayerRepository repo = new PlayerRepository(database);

        UUID id = UUID.randomUUID();
        repo.save(new PlayerRecord(id, "Steve", 100, 1L)).join();
        assertTrue(repo.exists(id).join());
        assertEquals(100, repo.find(id).join().orElseThrow().coins());
    }
}
```

Using `Runnable::run` as the async executor makes `CompletableFuture`s complete synchronously in tests — no thread timing issues.

## Maven Surefire configuration

The `pom.xml` configures `maven-surefire-plugin` with `--add-opens` arguments required by MockBukkit and Mockito on Java 21:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <argLine>
            --add-opens java.base/java.lang=ALL-UNNAMED
            --add-opens java.base/java.util=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

## Testing your own plugin

Follow the same patterns:

1. Create unit tests for pure logic (no MockBukkit needed).
2. Create MockBukkit tests for Bukkit API interactions.
3. Create H2 tests for database repositories.
4. Never declare your main class `final`.
5. Keep `SchedulerService` and `Database` injectable so tests can swap in synchronous executors.
