# Typed Config Wrappers

Raw key strings scattered across the codebase are fragile and hard to maintain. `AbstractConfig` lets you wrap a `Config` file in a strongly-typed class that centralizes key strings and defaults in one place.

## AbstractConfig

```java
public abstract class AbstractConfig {
    protected AbstractConfig(Plugin plugin, String fileName) { ... }

    public Config raw()      // access the underlying Config
    public void reload()     // reload from disk
    public void save()       // persist changes to disk
}
```

## Creating a typed config

Create a final class that extends `AbstractConfig` and exposes intent-revealing getters:

```java
public final class SettingsConfig extends AbstractConfig {

    public SettingsConfig(Plugin plugin) {
        super(plugin, "config.yml");
    }

    public String prefix() {
        return raw().getString("prefix", "<gray>[Core]</gray> ");
    }

    public boolean debug() {
        return raw().getBoolean("debug", false);
    }

    public int startingBalance() {
        return raw().getInt("economy.starting-balance", 100);
    }
}
```

## Registering with ConfigManager

Register the wrapper so it participates in hot-reload:

```java
ConfigManager configs = service(ConfigManager.class);
SettingsConfig settings = configs.register(new SettingsConfig(plugin));
// or provide it as its own service:
provide(SettingsConfig.class, settings);
```

## Using a typed config

```java
SettingsConfig settings = configs.get(SettingsConfig.class);
String prefix = settings.prefix();
boolean isDebug = settings.debug();
```

## Benefits

| Approach | Problem |
|---|---|
| `config.getString("economy.starting-balance")` | Typos compile silently; no IDE autocomplete for key names |
| `settings.startingBalance()` | Type-safe, refactorable, default in one place |

## Multiple config files

Each YAML file gets its own typed class:

```java
public final class DatabaseConfig extends AbstractConfig {
    public DatabaseConfig(Plugin plugin) { super(plugin, "database.yml"); }
    public boolean enabled() { return raw().getBoolean("enabled", false); }
    public String host()     { return raw().getString("host", "localhost"); }
}

public final class ArenaConfig extends AbstractConfig {
    public ArenaConfig(Plugin plugin) { super(plugin, "arenas.yml"); }
    public List<String> arenaNames() { return raw().getStringList("arenas"); }
}
```
