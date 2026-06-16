# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

DzusillCore is a reusable Paper plugin framework/template (Paper 1.21.1, Java 21, Maven). Clone it to start a new plugin — the `me.dzusill.core.example` package is a working reference that can be deleted once you have your own modules.

## Build & test

```bash
mvn package          # compile + shade → target/DzusillCore-<version>.jar
mvn test             # MockBukkit + JUnit 5 suite
mvn test -Dtest=FooTest   # single test class
```

The shade plugin relocates HikariCP to avoid classpath conflicts. The plugin main class must **not** be `final` (MockBukkit subclasses it for tests).

## Architecture

Plugins extend `CorePlugin` and return an ordered `CoreModule[]` from `modules()`. Do **not** override `onEnable`/`onDisable` directly.

```java
public class MyPlugin extends CorePlugin {
    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{ new FoundationModule(this), new CommandModule(this) };
    }
}
```

Modules enable in array order and disable in reverse. Each module publishes services via `provide(Type.class, instance)` and resolves upstream services via `service(Type.class)`. **Startup order is the single source of truth for service dependencies.**

### Package map

| Package | Responsibility |
|---|---|
| `me.dzusill.core` | `CorePlugin` bootstrap base |
| `module` | `CoreModule` / `AbstractModule` / `ModuleManager` lifecycle |
| `service` | `Service`, `ServiceRegistry`, `Reloadable` decoupling layer |
| `config` | `Config` (comment-preserving YAML), `ConfigManager`, `AbstractConfig` |
| `message` | `MessageService` (MiniMessage), `Messages`, `Placeholder` |
| `command` | `CoreCommand`, `SubCommand`, `CommandRegistry`, `argument/*` |
| `menu` | `Menu`, `PaginatedMenu`, `MenuItem`, `MenuManager`, `template/*` |
| `event` | `CoreListener`, `ListenerRegistry`, `@AutoRegister` |
| `hook` | `PluginHook`, `HookManager`, Vault/PAPI/Essentials hooks |
| `item` | `ItemDataStore` (key/value on an `ItemStack`): `PdcItemDataStore` (native, default) / `NbtApiItemDataStore` (raw NBT, legacy-format compat) |
| `storage` | `DataStore`, `AbstractDataStore`, `YamlDataStore` |
| `database` | `Database`, `MySqlDatabase`/`PostgreSqlDatabase`, `DatabaseManager`, `query/*`, `repository/*` |
| `scheduler` | `SchedulerService` (sync/async/delayed/repeating + async-to-sync) |
| `cooldown` | generic `CooldownManager<K>` |
| `util` | `ItemBuilder`, `ColorUtils`, `LocationUtils`, `TimeUtils`, `NumberUtils`, `TextUtils` |

## Key conventions

### Commands

Registered at runtime via `CommandRegistry` using Paper's `CommandMap` — **no `plugin.yml` entry needed**. Declare shape in the constructor; parsing, validation, and tab-completion are derived automatically.

```java
@CommandMeta(name = "heal", permission = "core.heal", playerOnly = true)
public final class HealCommand extends CoreCommand {
    public HealCommand() { optionalArg("target", new OnlinePlayerArgument()); }

    @Override
    public void run(CommandContext ctx, Arguments args) { /* ... */ }
}
```

Group subcommands by adding `child(new SubCommand())` in the parent constructor. Built-in argument types: `StringArgument`, `IntArgument`, `PlayerArgument`, `OnlinePlayerArgument`, `EnumArgument`, `MaterialArgument`. Custom types implement `ArgumentType<T>` (`parse` + `suggest`).

### Optional integrations (hooks)

Register hooks lazily by plugin name so the hook class is only loaded when that plugin is present on the server:

```java
HookManager hooks = new HookManager(plugin);
hooks.register("Vault", VaultHook::new);
hooks.register("PlaceholderAPI", PlaceholderApiHook::new);
```

### Database

MySQL and PostgreSQL via HikariCP. All `Database` methods are async (`CompletableFuture`). Resume on the main thread with `scheduler.mainThreadExecutor()` before touching the Bukkit API. Schema applied at startup from `schema-mysql.sql` / `schema-postgresql.sql`. The database is optional: `enabled: false` in `database.yml` lets the plugin run without it. For typed models, extend `AbstractSqlRepository<ID, T>`.

### Menus (GUI)

Layouts can be defined in `menus.yml` via `YamlMenuTemplate` so server owners can restyle GUIs without recompiling. `MenuManager` tracks open inventories; `MenuListener` handles clicks. Register via `ListenerRegistry`.

Clicks/drags are cancelled by default. Declare an editable slot with `inputSlot(int)` in `decorate()` (the player may place/take there), and override `onClose(InventoryCloseEvent)` to react when the menu closes (e.g. return a left-over input item). Persist item state via the `item` package's `ItemDataStore`.

### Item data (`item`)

`ItemDataStore` is the item-level analogue of the player-record `storage`/`database` layers — a tiny typed key/value API over an `ItemStack`. Default `PdcItemDataStore` (native PDC, plugin-namespaced keys, lower-cased). Use `NbtApiItemDataStore` only to read/preserve raw case-sensitive NBT tags from an existing item format; it needs `depend: [NBTAPI]` and cannot run under MockBukkit.

### Configs

`Config` preserves YAML comments on reload. `AbstractConfig` wraps a named config file with typed accessors. `ConfigManager` handles batch reload (e.g. from `/core reload`).
