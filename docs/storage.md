# File Storage

The `storage` package provides a typed, cache-backed key-value store that persists to YAML files. It uses the same `DataStore` interface as the SQL layer, so feature code can swap backends without changing logic.

## DataStore interface

```java
public interface DataStore<K, V> {
    Optional<V> get(K key);
    void put(K key, V value);
    boolean has(K key);
    void remove(K key);
    Map<K, V> all();
    void load();
    void save();
}
```

`load()` reads from the backing store into the in-memory cache. `save()` flushes the cache back to the backing store.

## AbstractDataStore

Base implementation with a `ConcurrentHashMap` cache. `load()` and `save()` are abstract; concrete subclasses implement persistence:

```java
public abstract class AbstractDataStore<K, V> implements DataStore<K, V> {
    protected final Map<K, V> cache = new ConcurrentHashMap<>();
    // get/put/has/remove/all implemented via cache
}
```

## YamlDataStore

YAML-backed store under a section of an existing `Config` file. Values are converted to/from YAML primitives by a serializer/deserializer pair:

```java
Config data = service(ConfigManager.class).load("data.yml");

YamlDataStore<Long> lastSeen = new YamlDataStore<>(
        data,
        "last-seen",               // YAML section
        value -> value,            // Long → Long (YAML stores it as a number)
        raw -> ((Number) raw).longValue());

lastSeen.load();
lastSeen.put(player.getUniqueId().toString(), System.currentTimeMillis());
lastSeen.save();
```

### Serializing a complex object

```java
YamlDataStore<Location> warps = new YamlDataStore<>(
        data,
        "warps",
        location -> LocationUtils.serialize(location),      // Location → String
        raw -> LocationUtils.deserialize((String) raw));    // String → Location

warps.load();
warps.put("spawn", player.getLocation());
warps.save();

Optional<Location> spawn = warps.get("spawn");
```

### Resulting data.yml layout

```yaml
warps:
  spawn: "world:64:100:64:0.0:0.0"
last-seen:
  550e8400-e29b-41d4-a716-446655440000: 1718471234567
```

## Choosing between YAML and SQL storage

| Scenario | Use |
|---|---|
| Small data sets (warps, server settings, simple flags) | `YamlDataStore` |
| Large or relational data, multi-server shared state | `SqlDataStore` (see [Database → Repositories](database/repositories.md)) |
| You want to decide later | Code against `DataStore<K,V>` — swap the implementation without touching callers |

## Load/save lifecycle

It is your responsibility to call `load()` during startup and `save()` on shutdown:

```java
@Override
public void onEnable() {
    warpStore.load();
}

@Override
public void onDisable() {
    warpStore.save();
}
```

Consider also saving periodically via `SchedulerService` to protect against crashes:

```java
scheduler.repeating(() -> warpStore.save(), 0L, 20L * 60 * 5); // every 5 minutes
```
