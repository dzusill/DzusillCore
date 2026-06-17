# Version Detection

`VersionDetector.detect()` returns an immutable `MinecraftVersion` describing the running server. It must be robust across the whole 1.16.5–1.21.x range, which crosses several breakpoints that catch out naive detection code:

- **1.17** — NMS moved from `net.minecraft.server.v1_16_R3.*` to `net.minecraft.*`.
- **1.18.1** — `org.bukkit.profile.PlayerProfile` + `SkullMeta.setOwnerProfile` became part of the Bukkit/Spigot API; `SkullTextures` switches strategy here.
- **1.20.5** — Paper stopped relocating CraftBukkit into a `vX_Y_RZ` package and switched to Mojang mappings. From here the classic "read the `v1_20_R4` suffix off the server package" trick **returns nothing**. `CraftMetaSkull.profile` field type also changed to `ResolvableProfile`.
- **1.21.4** — Paper tightened Java module access on CraftBukkit internal classes. Public methods on `CraftMetaSkull` throw `IllegalAccessException` when invoked via plain reflection unless looked up through the API interface (`SkullMeta`).

## Detection strategy

`detect()` tries the most reliable signal first:

```
1. Server#getMinecraftVersion()   (modern Paper, called reflectively) → "1.21.1"
2. Bukkit.getBukkitVersion()      (always present)                    → "1.21.1-R0.1-SNAPSHOT"
3. Server package name            (legacy only)                       → "…craftbukkit.v1_16_R3"
```

Steps 1–2 produce the semantic version (`major.minor.patch`). Step 3 is captured **separately and opportunistically** into `MinecraftVersion.craftBukkitTag()` — an `Optional` that is present only on servers up to 1.20.4, for adapters that still build relocated class names from it.

The Paper fast path is called reflectively because the framework compiles against Spigot 1.16.5, whose API doesn't declare `getMinecraftVersion()`.

## MinecraftVersion

```java
public record MinecraftVersion(int major, int minor, int patch, Optional<String> craftBukkitTag)
        implements Comparable<MinecraftVersion> {

    boolean isAtLeast(int major, int minor);
    boolean isAtLeast(int major, int minor, int patch);
    boolean isBefore(int major, int minor);
}
```

**Branch on the semantic version, never on package strings:**

```java
MinecraftVersion v = VersionDetector.detect();

if (v.isAtLeast(1, 17)) {
    // net.minecraft.* layout, Bukkit Player#getPing() available, …
} else {
    // legacy net.minecraft.server.<tag>.* layout
    String tag = v.craftBukkitTag().orElseThrow(); // e.g. "v1_16_R3"
}

if (v.isAtLeast(1, 20, 5)) {
    // Mojang-mapped, no relocated CraftBukkit package
    // CraftMetaSkull.profile is ResolvableProfile, not GameProfile
}

if (v.isAtLeast(1, 18, 1)) {
    // org.bukkit.profile.PlayerProfile API available
    // SkullTextures uses setOwnerProfile — no NMS access needed for heads
}
```

## Testing

`VersionDetector.detect()` runs under MockBukkit (it emulates a modern server), so detection and adapter selection are unit-tested. Exact NMS calls cannot run under MockBukkit — verify those on real servers (see [Adapters](adapters.md#manual-verification-matrix)).
