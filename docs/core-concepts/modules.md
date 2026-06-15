# Modules

Modules are the primary unit of organization in DzusillCore. Each module encapsulates one logical subsystem (configuration, commands, menus, a third-party integration, …) and manages its own lifecycle.

## CoreModule interface

```java
public interface CoreModule {
    String name();
    void onEnable() throws Exception;
    void onDisable();
}
```

## AbstractModule

The recommended base class. Provides access to the owning plugin and shortcuts for publishing and resolving services:

```java
public abstract class AbstractModule implements CoreModule {
    protected final CorePlugin plugin;

    protected <T extends Service> void provide(Class<T> type, T service) { ... }
    protected <T extends Service> T service(Class<T> type) { ... }
    protected ServiceRegistry services() { ... }

    @Override
    public void onDisable() { } // no-op by default
}
```

## Creating a module

```java
public final class EconomyModule extends AbstractModule {

    private PlayerRepository playerRepo;

    public EconomyModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Economy";
    }

    @Override
    public void onEnable() {
        DatabaseManager db = service(DatabaseManager.class);
        this.playerRepo = new PlayerRepository(db.database());
        provide(PlayerRepository.class, playerRepo);
        plugin.getLogger().info("Economy module ready.");
    }

    @Override
    public void onDisable() {
        // release resources, flush data, etc.
    }
}
```

## ModuleManager

`ModuleManager` is created by `CorePlugin` and drives module startup/shutdown. You never interact with it directly; just define the order in `modules()`.

**Enable order**: the array index — left to right.

**Disable order**: reverse — right to left. This ensures that modules which depend on others are always torn down before their dependencies.

**Rollback on failure**: if `module[i].onEnable()` throws, `ModuleManager` disables all previously enabled modules in reverse order before re-throwing, so the server never ends up in a half-initialized state.

## A real module ordering example

```java
@Override
protected CoreModule[] modules() {
    return new CoreModule[]{
            new FoundationModule(this),    // #1 — registers ConfigManager, MessageService, SchedulerService, ListenerRegistry
            new DatabaseModule(this),      // #2 — requires SchedulerService; registers DatabaseManager
            new IntegrationModule(this),   // #3 — registers HookManager with Vault/PAPI/Essentials hooks
            new MenuModule(this),          // #4 — registers MenuManager; registers MenuListener
            new CommandModule(this)        // #5 — registers commands that depend on services above
    };
}
```

## Tips

- Keep each module focused on one subsystem. A module that does too many things is a sign it should be split.
- Always call `provide()` before the end of `onEnable()` so later modules can resolve the service.
- Always release resources (close connections, unregister listeners, flush data) in `onDisable()`.
