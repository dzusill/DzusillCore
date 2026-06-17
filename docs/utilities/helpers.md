# Utility Helpers

Static utility classes in `me.dzusill.core.util` cover the most common plugin tasks.

---

## ItemBuilder

Fluent builder for `ItemStack`s with full MiniMessage support for names and lore.

```java
ItemStack item = new ItemBuilder(Material.DIAMOND)
        .name("<aqua>Shiny Diamond")
        .lore("<gray>A very shiny diamond", "<dark_gray>Worth a lot")
        .glow()
        .amount(3)
        .build();
```

| Method | Description |
|---|---|
| `name(String)` | MiniMessage display name |
| `name(Component)` | Adventure Component directly |
| `lore(String...)` | MiniMessage lore lines (varargs) |
| `lore(List<String>)` | MiniMessage lore lines (list) |
| `amount(int)` | Stack size |
| `glow()` | Enchantment glint without showing enchantment text |
| `flags(ItemFlag...)` | Add item flags |
| `ItemBuilder.head(String base64)` | **Static** — start a builder for a custom-textured `PLAYER_HEAD` |
| `skull(String base64)` | Apply a base64 texture to a `PLAYER_HEAD` item (use after `new ItemBuilder(Material.PLAYER_HEAD)`) |
| `pdc(key, type, value)` | Write a value to PersistentDataContainer |
| `build()` | Returns the finished `ItemStack` |

```java
// Custom head with texture — one-liner via the static head() factory
ItemStack plus = ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly...")
        .name("<green>Add")
        .build();

// Equivalent explicit form
ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
        .skull("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly...")
        .name("<yellow>Custom Head")
        .build();

// In a menu, as a button icon:
// button(11).icon(ItemBuilder.head(HEAD_PLUS).name("<green>Add").build()).onClick(...).add();

// PDC tag for identification
NamespacedKey key = new NamespacedKey(plugin, "item-type");
ItemStack tagged = new ItemBuilder(Material.NETHER_STAR)
        .name("<gold>Special Item")
        .pdc(key, PersistentDataType.STRING, "special")
        .build();
```

---

## ColorUtils

See [Colors & MiniMessage](../messages-and-colors/colors.md) for full documentation.

```java
ColorUtils.parse("<gradient:aqua:blue>Text</gradient>")
ColorUtils.parse(List.of("<gray>Line 1", "<gray>Line 2"))
ColorUtils.legacy("&aLegacy color")
ColorUtils.strip("<bold><red>Text")  // → "Text"
```

---

## LocationUtils

Serialize/deserialize `Location` objects for YAML storage.

**Format**: `world,x,y,z,yaw,pitch`

```java
// Serialize
String stored = LocationUtils.serialize(player.getLocation());
// → "world,64.0,100.0,64.0,0.0,0.0"

// Deserialize (returns null if world not loaded or format invalid)
Location loc = LocationUtils.deserialize(stored);
if (loc != null) {
    player.teleport(loc);
}
```

---

## TimeUtils

Format millisecond durations as human-readable strings (e.g. for cooldown messages).

```java
TimeUtils.format(0)         // "0s"
TimeUtils.format(3500)      // "3s"
TimeUtils.format(65000)     // "1m 5s"
TimeUtils.format(3661000)   // "1h 1m 1s"
TimeUtils.format(90061000)  // "1d 1h 1m 1s"
```

Usage with `CooldownManager`:

```java
long remaining = cooldown.remaining(player.getUniqueId());
player.sendMessage(ColorUtils.parse(
        "<red>Wait <yellow>" + TimeUtils.format(remaining) + "<red> before using this again."));
```

---

## NumberUtils

Safe parsing and compact abbreviation of numbers.

```java
// Safe parsing — never throws
NumberUtils.parseInt("42")        // Optional.of(42)
NumberUtils.parseInt("abc")       // Optional.empty()
NumberUtils.parseDouble("3.14")   // Optional.of(3.14)

// Abbreviation
NumberUtils.abbreviate(1500)       // "1.5K"
NumberUtils.abbreviate(2300000)    // "2.3M"
NumberUtils.abbreviate(1000000000) // "1B"
```

---

## TextUtils

String utilities for command handling.

```java
// Tab-completion filter — returns all candidates that start with token (case-insensitive)
TextUtils.partialMatches("st", List.of("Steve", "Alex", "Stone"))
// → ["Steve", "Stone"]

// Join args from an index
TextUtils.joinFrom(new String[]{"hello","world","foo"}, 1)
// → "world foo"
```

`partialMatches` is used internally by the command framework for all tab-completion; call it from custom `ArgumentType.suggest()` implementations.
