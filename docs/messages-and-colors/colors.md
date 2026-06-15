# Colors & MiniMessage

DzusillCore uses [Adventure's MiniMessage](https://docs.advntr.dev/minimessage/) as its text format throughout: item names, lore, menu titles, messages, and the prefix. All parsing goes through `ColorUtils.parse()`.

## ColorUtils

```java
// MiniMessage string → Component (non-italic by default)
Component c = ColorUtils.parse("<aqua>Hello World");

// List of lines → List<Component> (for item lore)
List<Component> lore = ColorUtils.parse(List.of("<gray>Line 1", "<gray>Line 2"));

// Legacy & color codes (&a, &6, ...) → Component
Component c = ColorUtils.legacy("&aGreen text");

// Strip all formatting → plain String
String plain = ColorUtils.strip("<bold><red>Text");
```

> **Important**: `parse()` is non-italic by default. This matches Minecraft's convention for custom item names and lore which are italicized automatically. Use `parse()` everywhere; reserve `legacy()` only for configs that still use `&` codes.

---

## Named colors

Standard Minecraft color names:

```
<black>  <dark_blue>  <dark_green>  <dark_aqua>  <dark_red>
<dark_purple>  <gold>  <gray>  <dark_gray>  <blue>  <green>
<aqua>  <red>  <light_purple>  <yellow>  <white>
```

```
<aqua>Hello <gold>World
```

---

## Hex colors

Two equivalent syntaxes:

```
<#55FFFF>Hex color
<color:#55FFFF>Also hex
```

---

## Gradients

Smooth color transitions. Uses two or more colors (named or hex, freely mixed):

```
<gradient:#55FFFF:#5555FF>Aqua to blue</gradient>
<gradient:aqua:blue>Named gradient</gradient>
<gradient:#FF0000:#00FF00:#0000FF>Three-color gradient</gradient>
```

With phase offset (−1.0 to 1.0):

```
<gradient:gold:red:0.5>Shifted gradient</gradient>
```

---

## Rainbow

```
<rainbow>Automatic rainbow text</rainbow>
<rainbow:!>Reversed rainbow</rainbow>
<rainbow:2>With phase offset</rainbow>
```

---

## Decorations

```
<bold>Bold</bold>
<italic>Italic</italic>
<underlined>Underlined</underlined>
<strikethrough>Strikethrough</strikethrough>
<obfuscated>Obfuscated</obfuscated>
```

Negate a decoration inherited from a parent:

```
<!italic>Not italic (useful inside item lore)
```

---

## Combining tags

Tags can be nested and combined freely:

```
<gradient:aqua:light_purple><bold>SERVER NAME</bold></gradient>
<aqua>Normal <gold><bold>Highlighted</bold></gold> continued
```

---

## In config files

Anywhere a value is read via `ColorUtils.parse()` or `MessageService`, you can use MiniMessage:

```yaml
# config.yml
prefix: "<gradient:#55FFFF:#5555FF>[Core]</gradient> "

# messages.yml
welcome: "<rainbow>Welcome to the server, %name%!</rainbow>"
no-permission: "<prefix><red>No permission."
```

---

## In Java code

```java
// Menu title
@Override
public Component title() {
    return ColorUtils.parse("<gradient:#A020F0:#FF55FF><bold>Shop</bold></gradient>");
}

// Item name via ItemBuilder
new ItemBuilder(Material.DIAMOND)
        .name("<gradient:aqua:blue>Special Diamond")
        .lore("<gray>A very rare item")
        .build();
```

---

## Client requirement

Hex colors, gradients, and rainbow require **Minecraft 1.16+**. Paper 1.21.1 (the framework's target) has full support.
