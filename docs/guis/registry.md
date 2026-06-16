# GUIs — Menu Registry

`MenuRegistry` is to menus what `CommandRegistry` is to commands: register a menu **once by key**,
then open it **by name** from anywhere — a command, a click handler in another menu, a hook — without
constructing the class directly. It resolves the player's `PlayerMenuContext`, enforces the menu's
`@MenuMeta` permission, and opens it.

## Registering

Register menus from your menu module, alongside publishing the `MenuManager`:

```java
public final class MenuModule extends AbstractModule {

    @Override
    public void onEnable() {
        MenuManager manager = new MenuManager();
        provide(MenuManager.class, manager);

        MenuRegistry menus = new MenuRegistry(plugin, manager, service(MessageService.class));
        provide(MenuRegistry.class, menus);
        menus.register("shop", ShopMenu::new);      // MenuFactory = (plugin, context) -> Menu

        service(ListenerRegistry.class).register(new MenuListener(plugin));
    }
}
```

A `MenuFactory` is just `(CorePlugin plugin, PlayerMenuContext context) -> Menu`, so a constructor
reference like `ShopMenu::new` is usually all you need. Keys are case-insensitive.

## Opening by key

```java
MenuRegistry menus = service(MenuRegistry.class);

menus.open(player, "shop");   // returns false if the key is unknown or the player lacks permission
```

`open` returns `true` when the menu opened, `false` when the key is unknown **or** the player fails
the menu's `@MenuMeta(permission = ...)` (in which case a `no-permission` message is sent). This is
the same gating model as commands.

### Seeding context data

To pass data into the menu (e.g. the item being edited), use the seeding overload — it runs against
the player's context before the menu is built, so `decorate()` can read it:

```java
menus.open(player, "editor", context -> context.set("item", heldItem));
```

```java
// inside the menu's decorate():
ItemStack item = context.get("item");
```

## Opening from a command

Commands and menus connect cleanly through the registry:

```java
@CommandMeta(name = "shop", permission = "core.shop", playerOnly = true)
public final class ShopCommand extends CoreCommand {

    private final MenuRegistry menus;

    public ShopCommand(MenuRegistry menus) {
        this.menus = menus;
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        menus.open(context.player(), "shop");
    }
}
```

## Direct construction still works

The registry is additive. You can always build and open a menu directly when you don't need a key:

```java
new ShopMenu(plugin, menus.context(player)).open();
```

Note that direct `open()` does **not** check the menu's permission — only `MenuRegistry.open`
enforces it. Gate the direct path yourself (or open through the registry) when permission matters.
