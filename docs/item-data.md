# Item Data

The `item` package stores small, typed values **directly on an `ItemStack`** — the item-level
counterpart to the player-record `storage`/`database` layers. Reach for it when state belongs to a
specific item rather than a player or the world: durability counters, ownership tags, upgrade
levels, kill/usage trackers.

## ItemDataStore interface

```java
public interface ItemDataStore {
    int getInt(ItemStack item, String key);
    ItemStack setInt(ItemStack item, String key, int value);
    String getString(ItemStack item, String key);
    ItemStack setString(ItemStack item, String key, String value);
    boolean hasKey(ItemStack item, String key);
    ItemStack removeKey(ItemStack item, String key);
    Set<String> keys(ItemStack item);
}
```

`ItemStack`s are value types, so every **mutating** method returns the updated stack — always use
the return value, never assume the argument was modified in place:

```java
item = data.setInt(item, "kills", data.getInt(item, "kills") + 1);
player.getInventory().setItemInMainHand(item);
```

## PdcItemDataStore (default)

Native, dependency-free implementation backed by the Bukkit `PersistentDataContainer`. Keys are
namespaced to the owning plugin (so they never clash with vanilla NBT or other plugins) and
lower-cased to satisfy `NamespacedKey`'s charset.

```java
ItemDataStore data = new PdcItemDataStore(plugin);

ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
sword = data.setInt(sword, "kills", 0);

boolean tracked = data.hasKey(sword, "kills");   // true
int kills        = data.getInt(sword, "kills");  // 0
```

This is the recommended default and is fully unit-testable under MockBukkit.

## NbtApiItemDataStore

Backed by [NBTAPI](https://github.com/tr7zw/Item-NBT-API), writing **raw, case-sensitive NBT root
tags**. Choose it only when you must read or preserve an existing item format — for example items
tagged `Playerkills` / `StatTag` that predate the persistent data container, or tags shared with
another plugin.

```java
ItemDataStore data = new NbtApiItemDataStore();
sword = data.setInt(sword, "Playerkills", 0);   // raw tag, exact casing preserved
```

Requirements and caveats:

* Add the NBTAPI plugin as a dependency in `plugin.yml`: `depend: [NBTAPI]` (or `softdepend`).
* The framework compiles against NBTAPI with `provided` scope — the server supplies the plugin.
* NBTAPI resolves NMS at runtime, so it **cannot run under MockBukkit**. Cover your calling logic
  against `PdcItemDataStore` (same interface) and verify the NBTAPI path on a live server.

## Choosing an implementation

| Scenario | Use |
|---|---|
| New plugin, no legacy format | `PdcItemDataStore` |
| Must read/preserve existing raw NBT tags (legacy or cross-plugin) | `NbtApiItemDataStore` |
| You want testable logic | Code against `ItemDataStore`; inject `PdcItemDataStore` in tests |

Because callers depend on the `ItemDataStore` interface, you can swap the backend without touching
business logic — the recommended pattern is to inject the store and pass `PdcItemDataStore` from
unit tests while wiring `NbtApiItemDataStore` in production.
