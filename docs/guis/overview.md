# GUIs — Overview

The GUI system is built on Bukkit's `InventoryHolder` pattern. Every menu holds its own `Inventory`, handles its own click events, and is tracked per-player — no global slot maps, no hard-coded switch statements.

## Key classes

| Class | Role |
|---|---|
| `Menu` | Abstract base for all GUIs — title, size, items, template, click dispatch |
| `PaginatedMenu` | Extends `Menu` with automatic page layout and navigation buttons |
| `MenuItem` | An `ItemStack` paired with an optional click handler (`Consumer<InventoryClickEvent>`) |
| `PlayerMenuContext` | Per-player object holding state, arbitrary data, and navigation history |
| `MenuManager` | Service — manages `PlayerMenuContext` instances and closes menus on shutdown |
| `MenuListener` | Routes `InventoryClickEvent`/`InventoryDragEvent` to the correct `Menu` |

## Creating a menu

```java
public final class MyMenu extends Menu {

    public MyMenu(CorePlugin plugin, PlayerMenuContext context) {
        super(plugin, context);
    }

    @Override
    public Component title() {
        return ColorUtils.parse("<dark_purple><bold>My Menu");
    }

    @Override
    public int size() {
        return 27;   // must be a multiple of 9
    }

    @Override
    protected MenuTemplate template() {
        return Templates.bordered();   // optional; null = no template
    }

    @Override
    protected void decorate() {
        set(13, MenuItem.of(
                new ItemBuilder(Material.EMERALD)
                        .name("<green>Click me!")
                        .lore("<gray>Does something cool")
                        .build(),
                event -> context.player().sendMessage(
                        ColorUtils.parse("<green>You clicked!"))));
    }
}
```

## Opening a menu

Always open through `MenuManager` to ensure the context exists:

```java
MenuManager menus = service(MenuManager.class);
PlayerMenuContext context = menus.context(player);
new MyMenu(plugin, context).open();
```

Or from a command:

```java
@Override
public void run(CommandContext context, Arguments args) throws CommandException {
    new MyMenu(plugin, menus.context(context.player())).open();
}
```

## MenuItem

```java
// Non-interactive display item
MenuItem.display(itemStack)

// Interactive item with click handler
MenuItem.of(itemStack, event -> { /* handle click */ })
```

Events are **always cancelled** to prevent item theft.

## Navigation

`PlayerMenuContext` maintains a navigation stack:

```java
// From inside a menu or click handler:
back()    // reopen the previous menu, or close if history is empty
close()   // close the inventory
refresh() // rebuild the menu in place (useful after state changes)
```

## PlayerMenuContext — sharing data between menus

```java
// In the first menu
context.set("selectedItem", someItem);

// In the next menu
ItemStack item = context.get("selectedItem");
```

## MenuManager

`MenuManager` creates and caches one `PlayerMenuContext` per player. When the plugin disables, it calls `closeAll()` to close every open menu:

```java
MenuManager menus = service(MenuManager.class);
menus.context(player);         // get or create context
menus.forget(player);          // remove context (call on PlayerQuitEvent)
menus.closeAll();              // close all open menus
```

## MenuListener

`MenuListener` is registered automatically by `MenuModule`. It intercepts all `InventoryClickEvent`s, checks if the top inventory's holder is a `Menu`, and delegates to `menu.handleClick(event)`. Drag events in menus are always cancelled.
