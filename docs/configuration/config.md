# Config Files

DzusillCore uses a custom `Config` class that extends Bukkit's `YamlConfiguration` with two important extras: **comment preservation** and **automatic sync** of new keys from bundled resources.

## How it works

When `Config.loadConfig()` is called, it:

1. Copies the bundled resource file to the plugin's data folder if the on-disk file doesn't exist.
2. Loads the on-disk YAML into memory.
3. Syncs any keys present in the bundled resource but missing from the on-disk file (so new defaults appear after an update without overwriting user edits).
4. Preserves comments from the bundled resource.

## Loading a raw config

```java
Config config = Config.loadConfig(plugin, "data.yml", "data.yml");
// or with sections to ignore during sync:
Config config = Config.loadConfig(plugin, "data.yml", "data.yml", "players");
```

Parameters:

| Parameter | Description |
|---|---|
| `plugin` | owning plugin |
| `resourcePath` | path to the bundled resource (relative to `resources/`) |
| `serverPath` | on-disk file name in the plugin data folder |
| `ignoredSections` | YAML top-level sections skipped during sync (e.g. player data sections) |

## Accessing values

`Config` extends `YamlConfiguration` so all standard getters work:

```java
String prefix = config.getString("prefix", "<gray>[Core]</gray> ");
boolean debug  = config.getBoolean("debug", false);
int amount     = config.getInt("economy.starting-balance", 100);
List<String> list = config.getStringList("blocked-worlds");
```

## Saving changes

```java
config.set("economy.starting-balance", 500);
config.save();
```

## The default config.yml

```yaml
# Prefix injected wherever <prefix> appears in messages.yml
prefix: "<gray>[<aqua>Core</aqua>]</gray> "

# Enables verbose debug logging to the console
debug: false
```

## ConfigManager

Use `ConfigManager` (a `Service`) to manage multiple configs centrally and reload them all at once:

```java
ConfigManager configs = service(ConfigManager.class);

// Load a raw config
Config data = configs.load("data.yml");

// Register a typed wrapper (see Typed Config)
SettingsConfig settings = configs.register(new SettingsConfig(plugin));
```

After registering, a single call to `configs.reload()` reloads every raw config and typed wrapper.
