# DzusillCore

A reusable Paper plugin template/framework. Clone it to start a new plugin with a modular
lifecycle, comment-preserving YAML configs, a MiniMessage message service, a declarative command
framework with automatic tab-completion, and a GUI menu system with reusable templates already in
place.

- **Platform:** Paper API 1.21.1
- **Java:** 21
- **Build:** Maven
- **Text:** Adventure + MiniMessage
- **Tests:** MockBukkit + JUnit 5 + Mockito

## Quickstart

1. Copy the project (use the **Use this template** button on GitHub or clone it).
2. Rename the project: update `groupId`, `artifactId`, and `name` in [pom.xml](pom.xml), and
   the `main` class path in [plugin.yml](src/main/resources/plugin.yml).
3. Replace the `me.dzusill.core.example` package with your own plugin class and modules. The
   example package is a working reference and can be deleted once you have your own.
4. Build: `mvn package`. The shaded jar lands in `target/`.

A minimal plugin entry point:

```java
public class MyPlugin extends CorePlugin {
    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{
                new FoundationModule(this),
                new CommandModule(this)
        };
    }
}
```

Modules are enabled in array order and disabled in reverse. Each module publishes the services it
owns into the shared `ServiceRegistry` and resolves the services it needs, so startup order is the
single source of truth for dependencies.

## Package overview

| Package | Responsibility |
| --- | --- |
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
| `permission` | `CorePermission` node constants |
| `util` | `ItemBuilder`, `ColorUtils`, `LocationUtils`, `TimeUtils`, `NumberUtils`, `TextUtils` |

## Adding a command

Commands are registered at runtime through `CommandRegistry` (no `plugin.yml` entry needed) and
declare their shape; parsing, validation and tab-completion are derived automatically.

```java
@CommandMeta(name = "heal", permission = "core.heal", playerOnly = true)
public final class HealCommand extends CoreCommand {
    public HealCommand() {
        optionalArg("target", new OnlinePlayerArgument()); // auto-completes online players
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        Player target = args.getOr("target", context.player());
        target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
    }
}
```

Group commands by registering children, which turns the parent into a router:

```java
@CommandMeta(name = "core", permission = "core.admin")
public final class CoreAdminCommand extends CoreCommand {
    public CoreAdminCommand(/* deps */) {
        child(new ReloadSubCommand(/* deps */));
    }
    @Override public void run(CommandContext ctx, Arguments args) { /* show help */ }
}
```

Built-in argument types live in `command.argument.types`: `StringArgument`, `IntArgument`,
`PlayerArgument`, `OnlinePlayerArgument`, `EnumArgument`, `MaterialArgument`. Add your own by
implementing `ArgumentType<T>` (`parse` + `suggest`).

## Adding a menu

```java
public final class ShopMenu extends Menu {
    public ShopMenu(CorePlugin plugin, PlayerMenuContext context) { super(plugin, context); }

    @Override public Component title() { return ColorUtils.parse("<dark_purple>Shop"); }
    @Override public int size() { return 27; }
    @Override protected MenuTemplate template() { return Templates.bordered(); }

    @Override
    protected void decorate() {
        set(13, MenuItem.of(
                new ItemBuilder(Material.DIAMOND).name("<aqua>Buy").glow().build(),
                event -> context.player().sendMessage(ColorUtils.parse("<green>Bought!"))));
    }
}

// open it:
new ShopMenu(plugin, menuManager.context(player)).open();
```

- Extend `PaginatedMenu` instead to get automatic paging and a navigation bar; just implement
  `content()`.
- Reuse layouts with `Templates.bordered()` / `Templates.filled()`, or define them in
  [menus.yml](src/main/resources/menus.yml) and apply with `new YamlMenuTemplate(config, path)`.
- Clicks are dispatched by the single `MenuListener` via the `InventoryHolder` pattern; menus never
  register their own listeners. `back()` uses the per-player navigation history.

## Adding a config

```java
public final class SettingsConfig extends AbstractConfig {
    public SettingsConfig(Plugin plugin) { super(plugin, "config.yml"); }
    public String prefix() { return raw().getString("prefix", "<gray>[Core]</gray> "); }
}

// register so it reloads with /core reload:
configManager.register(new SettingsConfig(plugin));
```

`Config` preserves comments and auto-adds new keys from the bundled resource across versions, so
shipped defaults can evolve without overwriting user edits.

## Using the database

MySQL and PostgreSQL are supported through a HikariCP-pooled, fully async layer. The database is
optional: with `enabled: false` in [database.yml](src/main/resources/database.yml) the plugin runs
normally and `DatabaseManager.isEnabled()` returns `false`.

Configure connection details in `database.yml`, then resolve the manager and use the `Database`:

```java
DatabaseManager dbManager = services.get(DatabaseManager.class);
if (dbManager.isEnabled()) {
    Database db = dbManager.database();
    db.queryOne("SELECT coins FROM core_players WHERE uuid = ?",
                rs -> rs.getInt("coins"),
                uuid.toString())
      .thenAcceptAsync(opt -> opt.ifPresent(coins -> /* touch Bukkit API */ {}),
                       scheduler.mainThreadExecutor());
}
```

Key points:

- Every `Database` method is async and returns a `CompletableFuture`, executed off the main thread.
  Resume on the main thread with `scheduler.mainThreadExecutor()` before touching the Bukkit API.
- Schema is applied at startup from `schema-mysql.sql` / `schema-postgresql.sql` (per dialect).
- Dialect differences (driver, URL, upsert syntax) live in the `DatabaseType` enum; adding a backend
  (e.g. SQLite) is one new constant plus a thin `Database` subclass.

For a typed model, extend `AbstractSqlRepository<ID, T>` (see
[PlayerRepository](src/main/java/me/dzusill/core/example/database/PlayerRepository.java)); the
insert-or-update is generated per dialect, so it runs unchanged on MySQL and PostgreSQL.

To stay storage-agnostic, use `SqlDataStore<V>`, a SQL-backed implementation of the same
`DataStore` interface as `YamlDataStore`, so a feature can swap YAML for SQL with no code changes.

## Optional integrations (hooks)

Soft dependencies (Vault, PlaceholderAPI, EssentialsX, …) are fully optional. Declare them in
`plugin.yml` under `softdepend`, keep their APIs at Maven `provided` scope, and register each hook
**lazily by plugin name** so the hook class is only loaded when the plugin is actually installed:

```java
HookManager hooks = new HookManager(plugin);
hooks.register("Vault", VaultHook::new);
hooks.register("PlaceholderAPI", PlaceholderApiHook::new);
```

The factory (constructor reference) runs only after the presence check, so a hook that imports a
soft-dependency's API types never triggers `NoClassDefFoundError` when that plugin is absent. This
is what lets the same template power trivial plugins (none of these installed) and full ones.

Resolve a hook only when it is active:

```java
hooks.get(VaultHook.class).ifPresent(vault -> {
    Economy economy = vault.economy();
    // ...
});
```

Add a new integration by extending `PluginHook` (presence check + `setup()` are handled by the
base class) and registering it the same way.

## Testing

`mvn test` runs the MockBukkit + JUnit 5 suite. Integration tests load `ExamplePlugin` through
MockBukkit; the plugin main class must not be `final` (MockBukkit subclasses it).
