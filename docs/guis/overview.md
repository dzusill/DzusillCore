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

Clicks are **cancelled by default** to prevent item theft — except on declared
[input slots](#input-slots) where the player is allowed to place and take items.

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

`MenuListener` is registered automatically by `MenuModule`. It intercepts all `InventoryClickEvent`s, checks if the top inventory's holder is a `Menu`, and delegates to `menu.handleClick(event)`. It also routes `InventoryCloseEvent` to [`onClose`](#close-handling). Drag events in menus are cancelled unless every affected slot is an [input slot](#input-slots).

## Input slots

By default a menu is read-only. To let the player place and take an item in a specific slot —
an item-editor GUI, an enchanting/upgrade input, an anvil-style rename — declare it as an **input
slot** from `decorate()`:

```java
@Override
protected void decorate() {
    setItem(11, MenuItem.display(new ItemBuilder(Material.ARROW).name("<gray>Put an item ->").build()));
    inputSlot(12);   // slot 12 is freely editable by the player
}
```

Clicks and drags into an input slot are not cancelled, so the item moves normally. Input slot
declarations are cleared and re-applied on every `open()`/`refresh()`.

## Close handling

Override `onClose(InventoryCloseEvent)` to react when the menu is closed — most importantly to
return an item left in an input slot so it is never lost:

```java
private static final int INPUT = 12;

@Override
protected void onClose(InventoryCloseEvent event) {
    ItemStack left = inventory.getItem(INPUT);
    if (left == null || left.getType().isAir()) {
        return;
    }
    inventory.setItem(INPUT, null);
    Player player = context.player();
    player.getInventory().addItem(left).values()
            .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
}
```
