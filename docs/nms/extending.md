# Extending for Forks

DzusillCore ships the reflective default so the template works everywhere with zero build setup. When a fork needs deep NMS that reflection can't do reliably — packet sending, fake entities, custom mappings — it registers its own `NmsAdapter` on the **same** `NmsAdapters` registry. Feature code keeps depending on the `NmsAdapter` interface, so supporting a new server version never touches it.

There are two routes, in order of recommendation.

## Route 1 — reflection override (no build changes)

Register a fork adapter for the versions you care about. Most-recently-registered wins, so it overrides the built-in default:

```java
new NmsModule(this, NmsAdapters.defaults()
        .register(v -> v.isAtLeast(1, 21), Mapped1_21Adapter::new));
```

`Mapped1_21Adapter` can extend `ReflectiveNmsAdapter` to inherit the working primitives and only add what it needs:

```java
public final class Mapped1_21Adapter extends ReflectiveNmsAdapter {

    public Mapped1_21Adapter(MinecraftVersion version) { super(version); }

    @Override
    public boolean supports(NmsFeature feature) {
        return feature == NmsFeature.PACKET_SENDING || super.supports(feature);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        Object handle = nmsHandle(player);
        Object connection = Reflection.getFieldValue(handle, Reflection.field(handle.getClass(), "connection"));
        Reflection.invoke(connection, Reflection.method(connection.getClass(), "send", /* Packet type */ ));
    }
}
```

Because the factory runs only when its predicate matches, the 1.21-specific class is never linked on other versions.

## Route 2 — Maven profile + provided server jar (deep NMS)

When you want to compile against real, type-safe NMS instead of reflecting:

1. Install a remapped server jar into your local Maven repo with Spigot **BuildTools** (`--remapped`) for each target version.
2. Add it as a `provided` dependency under a Maven **profile** so it never ships in your JAR and doesn't pollute the default build.
3. Write a version-specific `NmsAdapter` against those NMS types and register it via `NmsAdapters.register(...)`.

> **1.20.5+ caveat:** Paper switched to Mojang mappings and dropped the relocated CraftBukkit package. A plugin compiled against Mojang-mapped NMS must be re-obfuscated (or run with a runtime remapper) to load on a Spigot-mapped server. This is the painful part of real-NMS on Maven and the reason Route 1 is preferred for most forks. (Gradle + `paperweight-userdev` automates this, but migrating the build is out of scope for the template.)

Either route, the plugin never changes — only which `NmsAdapter` the registry selects.

## Where NMS code lives in a fork

Keep version-specific code isolated behind the interface, mirroring the core layout. A fork organises its NMS package **feature-first under the abstraction**, not scattered through the codebase:

```
me.yourname.yourplugin
├── nms/
│   ├── NmsAdapter.java          # extend or re-declare the contract your features need
│   ├── NmsModule.java           # registers your adapters + provides the chosen one
│   └── version/
│       ├── Adapter1_16.java     # one class per version (or version family)
│       ├── Adapter1_20.java
│       └── Adapter1_21.java
└── feature/
    └── … feature modules resolve service(NmsAdapter.class), never a concrete Adapter …
```

Rules of thumb:

- **Feature code never imports a `version.*` class or any `net.minecraft` / CraftBukkit type.** It depends only on `NmsAdapter` + `NmsFeature`. This is what keeps the JVM from linking the wrong version's classes.
- **One adapter per version (or family).** Group versions that share an implementation behind a single predicate (`v -> v.isAtLeast(1, 17) && v.isBefore(1, 20)`).
- **Add capabilities as `NmsFeature` constants + interface methods**, then implement them per adapter. Unsupported versions report `supports(...) == false` and degrade instead of crashing.
- **Route all reflection through `me.dzusill.core.nms.reflect.Reflection`** so lookups are cached and class resolution stays consistent across the breakpoints.
