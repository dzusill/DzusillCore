# Reloading

DzusillCore supports hot-reload of all configurations and messages without restarting the server.

## How reload works

Any service that implements the `Reloadable` interface can be reloaded:

```java
public interface Reloadable {
    void reload() throws Exception;
}
```

Both `ConfigManager` and `MessageService` implement `Reloadable`.

## Triggering a reload from a command

The built-in `ReloadSubCommand` (part of `CoreAdminCommand`) does this:

```java
@Override
public void run(CommandContext context, Arguments args) {
    try {
        configs.reload();    // reloads every registered Config and AbstractConfig
        messages.reload();   // reloads messages.yml
        context.reply(Messages.RELOAD_SUCCESS);
    } catch (Exception ex) {
        context.reply(Messages.RELOAD_FAILED);
    }
}
```

In-game: `/core reload` (requires `core.reload` permission).

## Reloading your own config

Register any typed config with `ConfigManager` so it is reloaded automatically:

```java
ConfigManager configs = service(ConfigManager.class);
configs.register(new MyCustomConfig(plugin));
```

Or implement `Reloadable` in your own service and call `reload()` manually from the command.

## What is NOT reloaded

- **Commands**: registered at startup via `CommandRegistry` and persist for the plugin's lifetime.
- **Listeners**: registered once in `ListenerRegistry` and do not need reloading.
- **Database connections**: the HikariCP pool is not restarted on reload. Credential changes require a plugin restart.

## The reload permission

Defined in `plugin.yml`:

```yaml
permissions:
  core.reload:
    description: Allows reloading the plugin configuration
    default: op
```
