# GUI Examples

## Example 1: ShopMenu — bordered template with a click handler

The reference `ShopMenu` demonstrates the minimum required for a working GUI: a title, a size, an optional template, and item placement in `decorate()`.

```java
public final class ShopMenu extends Menu {

    public ShopMenu(CorePlugin plugin, PlayerMenuContext context) {
        super(plugin, context);
    }

    @Override
    public Component title() {
        return ColorUtils.parse("<dark_purple>Example Shop");
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected MenuTemplate template() {
        return Templates.bordered();
    }

    @Override
    protected void decorate() {
        set(13, MenuItem.of(
                new ItemBuilder(Material.DIAMOND)
                        .name("<aqua>Buy a diamond")
                        .lore("<gray>Click to purchase")
                        .glow()
                        .build(),
                event -> context.player().sendMessage(
                        ColorUtils.parse("<green>Purchased a diamond!"))));
    }
}
```

Opening it from a command:

```java
@CommandMeta(name = "shop", permission = "core.shop", playerOnly = true)
public final class ShopCommand extends CoreCommand {

    private final MenuManager menus;

    public ShopCommand(MenuManager menus) {
        super();
        this.menus = menus;
    }

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        new ShopMenu(plugin, menus.context(context.player())).open();
    }
}
```

---

## Example 2: Gradient title

```java
@Override
public Component title() {
    return ColorUtils.parse("<gradient:#A020F0:#FF55FF><bold>My Shop</bold></gradient>");
}
```

---

## Example 3: Two-level navigation (parent → detail)

```java
// Parent menu — shows a category list
public final class CategoryMenu extends Menu {

    @Override
    protected void decorate() {
        set(11, MenuItem.of(weaponsIcon, event -> {
            // push context data and open detail menu
            context.set("category", "weapons");
            new DetailMenu(plugin, context).open();   // DetailMenu.back() returns here
        }));
    }
}

// Detail menu — back() returns to CategoryMenu
public final class DetailMenu extends Menu {

    @Override
    protected void decorate() {
        // Back button
        set(0, MenuItem.of(
                new ItemBuilder(Material.ARROW).name("<yellow>Back").build(),
                event -> back()));

        // Content items ...
    }
}
```

`PlayerMenuContext` automatically records `CategoryMenu` in the history when `DetailMenu.open()` is called, so `back()` always returns to the previous menu without any manual tracking.

---

## Example 4: Refresh after state change

```java
private int counter = 0;

@Override
protected void decorate() {
    set(13, MenuItem.of(
            new ItemBuilder(Material.CLOCK)
                    .name("<yellow>Count: " + counter)
                    .build(),
            event -> {
                counter++;
                refresh();   // rebuilds the menu in place without recording history
            }));
}
```
