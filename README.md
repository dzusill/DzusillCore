# DzusillCore

A free, open-source Paper plugin framework. Commands, GUIs, async database, MiniMessage messaging,
hot-reload config, cooldowns, and a module lifecycle — all wired together and ready to drop into any plugin.

**Platform:** Paper 1.21+ &nbsp;|&nbsp; **Java:** 21 &nbsp;|&nbsp; **Build:** Maven &nbsp;|&nbsp; **License:** Apache 2.0

📖 **[Full documentation on GitBook](https://dzusill.gitbook.io/dzusillcore)**

---

## Two ways to use it

### Option A — Maven dependency *(existing project)*

Add JitPack and the dependency to your `pom.xml`. Use `compile` scope so Maven shades DzusillCore
into your plugin's fat JAR — no separate server plugin needed on the server.

```xml
<repositories>
    <repository>
        <id>papermc-repo</id>
        <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- Paper API (provided — already on the server) -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21.1-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>

    <!-- DzusillCore — compile scope so it gets shaded into your JAR -->
    <dependency>
        <groupId>me.dzusill</groupId>
        <artifactId>DzusillCore</artifactId>
        <version>1.1.0</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

Configure the Maven Shade Plugin to bundle DzusillCore and exclude its resource files so they don't
overwrite yours:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.3</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <filters>
                    <!-- exclude DzusillCore's resource files — your plugin provides its own -->
                    <filter>
                        <artifact>me.dzusill:DzusillCore</artifact>
                        <excludes>
                            <exclude>plugin.yml</exclude>
                            <exclude>config.yml</exclude>
                            <exclude>messages.yml</exclude>
                            <exclude>database.yml</exclude>
                            <exclude>menus.yml</exclude>
                            <exclude>*.sql</exclude>
                        </excludes>
                    </filter>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                            <exclude>module-info.class</exclude>
                        </excludes>
                    </filter>
                </filters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **Note on Adventure:** DzusillCore shades and relocates Adventure to `me.dzusill.core.lib.kyori`.
> Import `Component` from there — never from `net.kyori` directly — to avoid classpath conflicts.

---

### Option B — GitHub template *(new plugin from scratch)*

Click **Use this template → Create a new repository** on GitHub to get a fresh repo with all
framework files and the working example plugin already in place.

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_PLUGIN.git
cd YOUR_PLUGIN
```

1. Update `groupId`, `artifactId`, and `name` in `pom.xml`.
2. Update `main` in `src/main/resources/plugin.yml`.
3. Replace the `me.dzusill.core.example` package with your own code (keep it as reference first).
4. `mvn package` — shaded JAR lands in `target/`.

Full walkthrough: [docs/getting-started/installation.md](docs/getting-started/installation.md)

---

## Real-world example — dDeathPenalty

[**dDeathPenalty**](https://github.com/dzusill/dDeathPenalty) is a full production plugin built on
DzusillCore. Browse its source to see every framework system in a real context:

| What it shows | Where |
|---|---|
| `CorePlugin` + ordered `CoreModule[]` | `DeathPenaltyPlugin.java` |
| `@CommandMeta` + `CoreCommand` + `SubCommand` routing | `command/DeathPenaltyCommand.java` |
| `AbstractConfig` + `ConfigManager` hot-reload | `config/DeathPenaltyConfig.java` |
| Vault / PlaceholderAPI / WorldGuard via `HookManager` | `module/IntegrationModule.java` |
| `CooldownManager<UUID>` for per-player rate limiting | `module/PenaltyModule.java` |
| MockBukkit + JUnit 5 unit tests | `src/test/` |

---

## Features

| System | What you get |
|---|---|
| **Module lifecycle** | `CoreModule[]` array; ordered enable/disable; `ServiceRegistry` for wiring |
| **Commands** | `@CommandMeta`, `CoreCommand`, `SubCommand` nesting, automatic tab-complete, 6 built-in argument types |
| **GUIs** | `@MenuMeta`, fluent `button()` API, permission-gated slots, `PaginatedMenu`, `YamlMenuTemplate` |
| **Database** | MySQL + PostgreSQL via HikariCP, fully async `CompletableFuture` API, `AbstractSqlRepository` |
| **Messaging** | `MessageService` + `messages.yml` + MiniMessage; falls back to legacy section-sign on plain Spigot |
| **Config** | `AbstractConfig`, comment-preserving YAML, `ConfigManager` reloads all at once |
| **Cooldowns** | Generic `CooldownManager<K>` keyed by any type (`UUID`, `String`, …) |
| **Hooks** | `HookManager` lazy-loads Vault / PlaceholderAPI / EssentialsX only when present |
| **Scheduler** | `SchedulerService` — sync, async, delayed, repeating, async-to-main-thread |
| **Storage** | `YamlDataStore` / `SqlDataStore` behind a common `DataStore<K,V>` interface |
| **NMS / multi-version** | `NmsAdapter` interface + `VersionDetector`; ships `ReflectiveNmsAdapter` (1.16.5–1.21.x) |
| **Utilities** | `ItemBuilder`, `ColorUtils`, `LocationUtils`, `TimeUtils`, `NumberUtils`, `TextUtils` |

---

## Quickstart

```java
public class MyPlugin extends CorePlugin {
    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{
            new FoundationModule(this),   // ConfigManager, MessageService, SchedulerService
            new CommandModule(this)        // CommandRegistry
        };
    }
}
```

Modules enable in array order and disable in reverse. Each module publishes services into the shared
`ServiceRegistry` and resolves the services it needs — startup order is the single source of truth.

---

## Package overview

| Package | Responsibility |
|---|---|
| `me.dzusill.core` | `CorePlugin` bootstrap base |
| `module` | `CoreModule` / `AbstractModule` / `ModuleManager` lifecycle |
| `service` | `Service`, `ServiceRegistry`, `Reloadable` decoupling layer |
| `config` | `Config` (comments + auto-sync), `ConfigManager`, `AbstractConfig` |
| `message` | `MessageService` (MiniMessage), `Messages`, `Placeholder` |
| `command` | `CoreCommand`, `SubCommand`, `CommandRegistry`, `argument/*` |
| `menu` | `Menu`, `PaginatedMenu`, `MenuItem`, `MenuManager`, `template/*` |
| `event` | `CoreListener`, `ListenerRegistry`, `@AutoRegister` |
| `hook` | `PluginHook`, `HookManager`, Vault/PAPI/Essentials hooks |
| `storage` | `DataStore`, `AbstractDataStore`, `YamlDataStore` |
| `database` | `Database`, `MySqlDatabase`/`PostgreSqlDatabase`, `DatabaseManager`, `query/*`, `repository/*` |
| `scheduler` | `SchedulerService` (sync/async/delayed/repeating + async-to-sync) |
| `cooldown` | generic `CooldownManager<K>` |
| `nms` | `NmsAdapter`, `VersionDetector`, `MinecraftVersion`, version adapters |
| `util` | `ItemBuilder`, `ColorUtils`, `LocationUtils`, `TimeUtils`, `NumberUtils`, `TextUtils` |

---

## Adding a command

```java
@CommandMeta(name = "heal", permission = "myplugin.heal", playerOnly = true)
public final class HealCommand extends CoreCommand {
    public HealCommand() {
        optionalArg("target", new OnlinePlayerArgument());
    }

    @Override
    public void run(CommandContext ctx, Arguments args) {
        Player target = args.getOr("target", ctx.player());
        target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
    }
}
```

Group subcommands with `child(new SomeSubCommand())` in the parent constructor. Built-in types:
`StringArgument`, `IntArgument`, `PlayerArgument`, `OnlinePlayerArgument`, `EnumArgument`,
`MaterialArgument`. Custom types implement `ArgumentType<T>` (`parse` + `suggest`).

---

## Adding a menu

```java
@MenuMeta(title = "<dark_purple>Shop", size = 27)
public final class ShopMenu extends Menu {
    public ShopMenu(CorePlugin plugin, PlayerMenuContext ctx) { super(plugin, ctx); }

    @Override
    protected void decorate() {
        button(13)
            .icon(new ItemBuilder(Material.DIAMOND).name("<aqua>Buy").glow().build())
            .onClick(e -> context.player().sendMessage(ColorUtils.parse("<green>Bought!")))
            .add();
    }
}
```

Extend `PaginatedMenu` for automatic paging. Define layouts in `menus.yml` via `YamlMenuTemplate`
so server owners can restyle GUIs without recompiling.

---

## Adding a config

```java
public final class SettingsConfig extends AbstractConfig {
    public SettingsConfig(Plugin plugin) { super(plugin, "config.yml"); }
    public String prefix() { return raw().getString("prefix", "<gray>[Core]</gray> "); }
}

// register so it reloads with /myplugin reload:
configManager.register(new SettingsConfig(plugin));
```

---

## Using the database

```java
DatabaseManager dbManager = services.get(DatabaseManager.class);
if (dbManager.isEnabled()) {
    dbManager.database()
        .queryOne("SELECT coins FROM players WHERE uuid = ?",
                  rs -> rs.getInt("coins"), uuid.toString())
        .thenAcceptAsync(opt -> opt.ifPresent(coins -> { /* Bukkit API here */ }),
                         scheduler.mainThreadExecutor());
}
```

Every `Database` method is async (`CompletableFuture`). Resume on the main thread with
`scheduler.mainThreadExecutor()` before touching the Bukkit API. Set `enabled: false` in
`database.yml` to skip it entirely. For typed models extend `AbstractSqlRepository<ID, T>`.

---

## Optional integrations (hooks)

```java
HookManager hooks = new HookManager(plugin);
hooks.register("Vault", VaultHook::new);
hooks.register("PlaceholderAPI", PlaceholderApiHook::new);

// resolve later:
hooks.get(VaultHook.class).ifPresent(vault -> {
    Economy economy = vault.economy();
});
```

Hook classes are only classloaded when the target plugin is present — `NoClassDefFoundError` never
fires. Add new integrations by extending `PluginHook`.

---

## Testing

```bash
mvn test
```

Uses MockBukkit 3.133.2 + JUnit 5 + Mockito 5. No live server needed. Plugin main class must not
be `final` (MockBukkit subclasses it for test isolation).

---

## Documentation

📖 **[dzusill.gitbook.io/dzusillcore](https://dzusill.gitbook.io/dzusillcore)**

Covers installation, core concepts, commands, GUIs, database, storage, events, integrations, NMS &
multi-version support, and testing.

---

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
