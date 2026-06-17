# NMS & Multi-Version

DzusillCore is **version-safe by construction**: it compiles against the lowest supported API surface (`spigot-api:1.16.5`), uses only the Bukkit/Spigot API, and bridges the few cross-version differences explicitly at runtime (`MessageService`, `SkullTextures`). That covers the vast majority of plugin work without ever touching server internals.

Some features can't be done through the Bukkit API and require `net.minecraft.server` (**NMS**) internals — packets, fake entities, direct world/entity access, connection latency on old servers, and so on. A single plugin JAR that runs across many Minecraft versions therefore needs a **version abstraction layer** (the professional name for the "NMS handling" pattern): one stable interface the plugin codes against, plus one implementation chosen per server version at runtime.

The `me.dzusill.core.nms` package provides exactly that, reusing the same building blocks as the rest of the framework.

## How it fits the existing patterns

| Existing pattern | NMS equivalent |
|---|---|
| `HookManager` loads a hook class only after a presence check | `NmsAdapters` instantiates **only** the adapter matching the running version — other versions' classes never link |
| Modules `provide(...)` / `service(...)` via `ServiceRegistry` | `NmsModule` does `provide(NmsAdapter.class, adapter)`; consumers `service(NmsAdapter.class)` |
| `CoreModule[]` order is the dependency graph | `NmsModule` runs right after the foundation module |
| Runtime feature detection (`MessageService`) | `NmsAdapter.isSupported()` + `supports(NmsFeature)` with a graceful no-op fallback |

## The pieces

```
me.dzusill.core.nms
├── MinecraftVersion       value: major/minor/patch + legacy CraftBukkit tag, with isAtLeast(...)
├── VersionDetector        detects the running version, robust across 1.17 and 1.20.5
├── NmsAdapter             the stable contract the plugin codes against (a Service)
├── NmsFeature             capability flags for supports(...) checks
├── NmsAdapters            ordered registry that selects + lazily instantiates the right adapter
├── NmsModule              wires detection → selection → provide() into the module system
├── reflect/Reflection     shared reflection toolkit (CraftBukkit/NMS class + member resolution)
└── version/
    ├── ReflectiveNmsAdapter   broad reflection default (ships in core)
    └── NoOpNmsAdapter         fallback when no adapter matches
```

## Quick start

`NmsModule` is already wired into the reference `ExamplePlugin` right after the foundation module:

```java
@Override
protected CoreModule[] modules() {
    return new CoreModule[]{
            new FoundationModule(this),
            new NmsModule(this),          // detect version, select + publish the adapter
            // … feature modules that resolve service(NmsAdapter.class) …
    };
}
```

Any later module or command resolves and uses it — always gating version-sensitive calls behind `supports(...)`:

```java
NmsAdapter nms = service(NmsAdapter.class);
if (nms.supports(NmsFeature.PLAYER_PING)) {
    int ping = nms.getPing(player);
}
```

## Cross-version item handling — SkullTextures

Player head textures are one of the most version-sensitive areas of the Bukkit API. `util.SkullTextures` handles this transparently across the full 1.16.5–1.21.x range with two runtime-selected strategies:

| Strategy | Versions | How |
|---|---|---|
| **Bukkit `PlayerProfile` API** (preferred) | Spigot/Paper 1.18.1+ | `Server.createProfile(UUID, name)` → `PlayerTextures.setSkin(URL)` → `SkullMeta.setOwnerProfile(PlayerProfile)`. Called via the `SkullMeta` **interface** handle (not `CraftMetaSkull`) to avoid the `IllegalAccessException` Paper's module restrictions put on unexported CraftBukkit packages. Base64 is decoded to extract the skin URL. |
| **GameProfile field reflection** (fallback) | 1.16.5–1.17.x | Private-field set on `CraftMetaSkull`. On 1.20.5+ Paper the field type changed to `ResolvableProfile`; `coerceProfile()` wraps the `GameProfile` via its `(GameProfile)` constructor, so the assignment still works. |

You never call `SkullTextures` directly — it is used internally by `ItemBuilder.head(base64)`.

### Version breakpoints that matter for skull textures

| Server version | What changed |
|---|---|
| 1.18.1 | `org.bukkit.profile.PlayerProfile` + `SkullMeta.setOwnerProfile` added to Bukkit/Spigot API |
| 1.20.5 | Paper switched CraftBukkit to Mojang mapping; `CraftMetaSkull.profile` became `ResolvableProfile` instead of `GameProfile` |
| 1.21.4 | Paper tightened module access — public methods on `CraftMetaSkull` now throw `IllegalAccessException` via plain reflection unless called through the API interface |

## What ships in core vs. what a fork adds

DzusillCore is a **hybrid**: core ships the reflective default (`ReflectiveNmsAdapter`) and the cross-version `SkullTextures` utility so the template works everywhere with zero build setup, and exposes a documented extension point so a fork can register a mapped per-version adapter when it needs deep NMS (e.g. packet sending). See [Adapters](adapters.md) and [Extending for Forks](extending.md).
