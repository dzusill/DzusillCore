# Project Structure

## Directory layout

```
DzusillCore/
├── src/
│   ├── main/
│   │   ├── java/me/dzusill/core/
│   │   │   ├── CorePlugin.java          ← abstract plugin base
│   │   │   ├── command/                 ← command framework
│   │   │   ├── config/                  ← YAML config system
│   │   │   ├── cooldown/               ← cooldown manager
│   │   │   ├── database/               ← SQL database layer
│   │   │   ├── event/                  ← listener infrastructure
│   │   │   ├── hook/                   ← soft-dependency hooks
│   │   │   ├── menu/                   ← GUI system
│   │   │   ├── message/                ← message service
│   │   │   ├── module/                 ← module lifecycle
│   │   │   ├── permission/             ← permission constants
│   │   │   ├── scheduler/              ← task scheduling
│   │   │   ├── service/                ← service registry
│   │   │   ├── storage/                ← file-based storage
│   │   │   └── util/                   ← utility helpers
│   │   │
│   │   └── java/me/dzusill/core/example/   ← reference implementation
│   │       ├── ExamplePlugin.java
│   │       ├── command/
│   │       ├── database/
│   │       ├── listener/
│   │       ├── menu/
│   │       └── module/
│   │
│   └── test/java/me/dzusill/core/      ← test suite
│
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml
│   ├── messages.yml
│   ├── menus.yml
│   ├── database.yml
│   ├── schema-mysql.sql
│   └── schema-postgresql.sql
│
├── docs/                               ← this documentation
├── .gitbook.yaml
└── pom.xml
```

## Package overview

| Package | Responsibility |
|---|---|
| `me.dzusill.core` | `CorePlugin` abstract base |
| `me.dzusill.core.module` | `CoreModule`, `AbstractModule`, `ModuleManager` — lifecycle |
| `me.dzusill.core.service` | `Service`, `Reloadable`, `ServiceRegistry` — dependency injection |
| `me.dzusill.core.config` | `Config`, `AbstractConfig`, `ConfigManager` — YAML config |
| `me.dzusill.core.message` | `MessageService`, `Messages`, `Placeholder` |
| `me.dzusill.core.command` | `CoreCommand`, `SubCommand`, `@CommandMeta`, argument system |
| `me.dzusill.core.menu` | `Menu`, `PaginatedMenu`, `MenuTemplate`, `MenuItem`, `MenuManager` |
| `me.dzusill.core.event` | `CoreListener`, `ListenerRegistry`, `@AutoRegister` |
| `me.dzusill.core.hook` | `PluginHook`, `HookManager`, Vault/PAPI/Essentials hooks |
| `me.dzusill.core.storage` | `DataStore`, `AbstractDataStore`, `YamlDataStore` |
| `me.dzusill.core.database` | `Database`, drivers, `DatabaseManager`, query/repository layer |
| `me.dzusill.core.scheduler` | `SchedulerService` |
| `me.dzusill.core.cooldown` | `CooldownManager` |
| `me.dzusill.core.permission` | `CorePermission` constants |
| `me.dzusill.core.util` | `ItemBuilder`, `ColorUtils`, `LocationUtils`, `TimeUtils`, `NumberUtils`, `TextUtils` |
| `me.dzusill.core.example` | Reference plugin — rename and replace for your own plugin |

## The example package

The `me.dzusill.core.example` package is a **fully functional reference plugin** that demonstrates every feature. It is the class declared as `main` in `plugin.yml`. When building your own plugin:

1. Rename the package (e.g. `me.yourname.myplugin`).
2. Rename `ExamplePlugin` to your main class and update `plugin.yml`.
3. Replace example modules, commands, and menus with your own.
4. Delete or repurpose `PlayerRecord`/`PlayerRepository` and `ShopMenu`.
