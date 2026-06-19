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
| `menu` | `Menu`, `PaginatedMenu`, `MenuItem`, `MenuButton`, `MenuManager`, `MenuRegistry`, `MenuFactory`, `meta/MenuMeta`, `template/*` |
| `event` | `CoreListener`, `ListenerRegistry`, `@AutoRegister` |
| `hook` | `PluginHook`, `HookManager`, Vault/PAPI/Essentials hooks |
| `nms` | `NmsAdapter` (version abstraction), `VersionDetector`/`MinecraftVersion`, `NmsAdapters` (selector), `NmsModule`, `reflect/Reflection`, `version/*` adapters |
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

### NMS / multi-version

When a feature needs `net.minecraft.server` internals (packets, fake entities, ping on old servers), code against the `NmsAdapter` interface — never a concrete version class. `NmsModule` (placed right after the foundation module) runs `VersionDetector.detect()`, asks `NmsAdapters.select(...)` for the matching adapter, and publishes it; consumers resolve `service(NmsAdapter.class)` and gate calls with `adapter.supports(NmsFeature.X)`. Core ships a reflection-only `ReflectiveNmsAdapter` (1.16.5–1.21.x, no server jar) plus a `NoOpNmsAdapter` fallback. The registry lazily instantiates only the matched adapter (like `HookManager`), so other versions' classes never link. **Branch on `MinecraftVersion.isAtLeast(major, minor)`, not on the CraftBukkit package string** — the `vX_Y_RZ` suffix disappears on Paper 1.20.5+. Forks add deep NMS by registering their own adapter on `NmsAdapters` (override wins); see `docs/nms/extending.md`.

### Database

MySQL and PostgreSQL via HikariCP. All `Database` methods are async (`CompletableFuture`). Resume on the main thread with `scheduler.mainThreadExecutor()` before touching the Bukkit API. Schema applied at startup from `schema-mysql.sql` / `schema-postgresql.sql`. The database is optional: `enabled: false` in `database.yml` lets the plugin run without it. For typed models, extend `AbstractSqlRepository<ID, T>`.

**Critical:** Pass a plain Java `ExecutorService` (`Executors.newCachedThreadPool()`) to `DatabaseManager`, **never** `SchedulerService.asyncExecutor()`. `SchemaInitializer` calls `.join()` on the main thread during `onEnable`; Bukkit's scheduler only dispatches tasks after the tick loop starts (after all `onEnable()` calls finish) — using it here causes a deadlock. See `example/module/DatabaseModule.java`.

### Menus (GUI)

Layouts can be defined in `menus.yml` via `YamlMenuTemplate` so server owners can restyle GUIs without recompiling. `MenuManager` tracks open inventories; `MenuListener` handles clicks. Register via `ListenerRegistry`.

Menus are declared like commands: annotate with `@MenuMeta(title, size, permission)` (instead of overriding `title()`/`size()`) and declare slots with the fluent `button(slot).icon(...).permission(...).onClick(...).add()` API. A `MenuButton` with a permission/`visibleIf` is auto-hidden during render and click-guarded, mirroring command per-node gating. Register menus by key on `MenuRegistry` (`menus.register("shop", ShopMenu::new)`) and open by name (`menus.open(player, "shop")`, which enforces the menu's permission) — the GUI analogue of `CommandRegistry`. The annotation/button/registry layers are additive: existing menus using `set(slot, MenuItem.of(...))` and overridden `title()`/`size()` keep working.

Clicks/drags are cancelled by default. Declare an editable slot with `inputSlot(int)` in `decorate()` (the player may place/take there), and override `onClose(InventoryCloseEvent)` to react when the menu closes (e.g. return a left-over input item). Persist item state via the `item` package's `ItemDataStore`.

### Item data (`item`)

`ItemDataStore` is the item-level analogue of the player-record `storage`/`database` layers — a tiny typed key/value API over an `ItemStack`. Default `PdcItemDataStore` (native PDC, plugin-namespaced keys, lower-cased). Use `NbtApiItemDataStore` only to read/preserve raw case-sensitive NBT tags from an existing item format; it needs `depend: [NBTAPI]` and cannot run under MockBukkit.

### Events / listeners

Extend `CoreListener` and register via `ListenerRegistry`. `@AutoRegister` on a `CoreListener` subclass lets `ListenerRegistry.registerAll(plugin)` pick it up without an explicit call — useful for listeners that have no external dependencies and should always be active.

```java
public class MyListener extends CoreListener {
    public MyListener(CorePlugin plugin) { super(plugin); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { /* ... */ }
}
// in a module:
listenerRegistry.register(new MyListener(plugin));
```

### Storage

`YamlDataStore` (in the `storage` package) is a simple file-backed key/value store for lightweight per-player or per-key data that doesn't need SQL. Extend `AbstractDataStore` or use `YamlDataStore` directly when all you need is load/save without querying. Use the `database` package only when you need relational queries, joins, or async HikariCP pooling.

### Messages (`messages.yml`)

All user-facing strings live in `messages.yml` in MiniMessage format. `<prefix>` is replaced by the configured prefix string. Named placeholders use `%name%` syntax; positional use `{0}`, `{1}`, etc. Add new keys to `messages.yml` and reference them via a constant in `Messages.java`. Missing keys fall back to the key string itself (visible in-game rather than silent).

### Configs

`Config` preserves YAML comments on reload. `AbstractConfig` wraps a named config file with typed accessors. `ConfigManager` handles batch reload (e.g. from `/core reload`).
