# Adapters

An **adapter** is one implementation of the `NmsAdapter` contract for a given version (or range). Exactly one is selected at startup by `NmsAdapters`, published by `NmsModule`, and resolved everywhere else with `service(NmsAdapter.class)`. Feature code depends only on the interface — adding a server version never touches it.

## The contract

```java
public interface NmsAdapter extends Service {
    MinecraftVersion version();
    boolean isSupported();                 // false for the no-op fallback
    boolean supports(NmsFeature feature);  // gate version-sensitive calls with this

    Object nmsHandle(Object craftBukkitObject);   // CraftPlayer → ServerPlayer/EntityPlayer
    void   sendPacket(Player player, Object packet);
    int    getPing(Player player);
}
```

`NmsFeature` is a small starter set of capability flags (`NMS_HANDLE`, `PACKET_SENDING`, `PLAYER_PING`). **Always gate calls** — on an unmapped version the active adapter may be the no-op, whose capability methods throw:

```java
NmsAdapter nms = service(NmsAdapter.class);
if (nms.supports(NmsFeature.PLAYER_PING)) {
    player.sendMessage("Ping: " + nms.getPing(player) + "ms");
}
```

## Built-in adapters

### ReflectiveNmsAdapter

Ships in core; selected for any 1.16+ server. Reflection-only — no server jar, no BuildTools — so the template works everywhere out of the box. It implements the primitives reflection can do **reliably** across the breakpoints:

| Feature | Supported | How |
|---|---|---|
| `NMS_HANDLE` | ✅ | `getHandle()` exists on every CraftBukkit wrapper |
| `PLAYER_PING` | ✅ | Bukkit-native `Player#getPing()` from 1.17; the NMS `EntityPlayer.ping` field before |
| `PACKET_SENDING` | ❌ | NMS connection names are obfuscated per version → needs a mapped adapter (see [Extending](extending.md)) |

`PACKET_SENDING` is deliberately left off: reliable packet work past 1.17 needs version-specific mapped names, which is exactly the reason to add a fork adapter rather than fight reflection.

### NoOpNmsAdapter

The fallback when nothing matches (e.g. a server older than 1.16 in lenient mode). `isSupported()` and every `supports(...)` return `false`, and capability methods throw a clear `UnsupportedOperationException` naming the feature and version — so a missing `supports(...)` gate fails loudly with a useful message instead of mysteriously.

## Selection — NmsAdapters

```java
NmsAdapters.defaults()        // reflective adapter for 1.16+, NoOp below
    .register(predicate, factory)  // add/override (most recent match wins)
    .strict()                      // throw UnsupportedVersionException instead of NoOp
    .select(VersionDetector.detect());
```

The factory is invoked **only** when its predicate matches the selected version — so a version-specific adapter class (and the classes it imports) is never linked by the JVM on a different server. This is the same lazy-loading guarantee `HookManager` gives for hooks.

## The Reflection toolkit

`me.dzusill.core.nms.reflect.Reflection` is the shared helper adapters use to resolve CraftBukkit/NMS classes and members across versions, with caching. It generalises the `getHandle()` + private-field style already used by `util.SkullTextures`:

```java
Object handle = Reflection.getHandle(player);                  // EntityPlayer / ServerPlayer
Class<?> craftPlayer = Reflection.craftBukkitClass("entity.CraftPlayer");
Field ping = Reflection.field(handle.getClass(), "ping");
```

The CraftBukkit base package is resolved lazily on first use, so the toolkit loads cleanly under MockBukkit.

## Manual verification matrix

NMS calls can't execute under MockBukkit (no real server), so the unit tests cover version parsing and adapter selection only. Verify real behaviour by dropping the built JAR on real Paper servers at the range endpoints and checking the startup log line (`NMS adapter: … for <version>`) plus any capability you rely on:

| Version | Why it matters |
|---|---|
| 1.16.5 | Legacy relocated NMS package; `craftBukkitTag` present |
| 1.18.2 | Post-1.17 `net.minecraft.*` layout |
| 1.20.4 | Last version with the relocated CraftBukkit package |
| 1.21.1 | Mojang-mapped, no relocation (`craftBukkitTag` empty) |
