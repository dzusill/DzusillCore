# Listeners & Events

## CoreListener

All event listeners in DzusillCore extend `CoreListener`, a thin abstract base that provides typed access to the owning plugin:

```java
public abstract class CoreListener implements Listener {
    protected final CorePlugin plugin;

    protected CoreListener(CorePlugin plugin) {
        this.plugin = plugin;
    }
}
```

## Creating a listener

```java
public final class PlayerJoinListener extends CoreListener {

    private final MessageService messages;

    public PlayerJoinListener(CorePlugin plugin, MessageService messages) {
        super(plugin);
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        messages.send(event.getPlayer(), "welcome",
                Placeholder.of("name", event.getPlayer().getName()));
    }
}
```

## ListenerRegistry

`ListenerRegistry` (a `Service`) centralizes listener registration and provides a single point to unregister everything on shutdown:

```java
ListenerRegistry listeners = service(ListenerRegistry.class);

// Unconditional registration
listeners.register(new PlayerJoinListener(plugin, messages));
listeners.register(new PlayerQuitListener(plugin, menus));

// Unregister all on disable
listeners.unregisterAll();
```

## @AutoRegister

Mark a listener with `@AutoRegister` to make it eligible for batch registration:

```java
@AutoRegister
public final class PlayerSessionListener extends CoreListener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.forget(event.getPlayer());
    }
}
```

Then register all annotated candidates at once:

```java
listeners.registerAnnotated(
        new PlayerSessionListener(plugin, menus),
        new SomeOtherListener(plugin)   // ignored if not @AutoRegister
);
```

This pattern lets a module register a batch of listeners and control which ones are active based on annotations, without modifying the registration call site.

## Registering from a module

```java
public final class MenuModule extends AbstractModule {

    @Override
    public void onEnable() {
        MenuManager menuManager = new MenuManager();
        provide(MenuManager.class, menuManager);

        ListenerRegistry listeners = service(ListenerRegistry.class);
        listeners.register(new MenuListener(plugin));
        listeners.registerAnnotated(new PlayerSessionListener(plugin, menuManager));
    }

    @Override
    public void onDisable() {
        service(MenuManager.class).closeAll();
    }
}
```

## Reference example: PlayerSessionListener

```java
@AutoRegister
public final class PlayerSessionListener extends CoreListener {

    private final MenuManager menus;

    public PlayerSessionListener(CorePlugin plugin, MenuManager menus) {
        super(plugin);
        this.menus = menus;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.forget(event.getPlayer());
    }
}
```

This ensures player menu contexts are cleaned up when a player leaves, preventing memory leaks.
