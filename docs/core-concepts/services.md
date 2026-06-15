# Services

The `ServiceRegistry` is a lightweight, type-keyed service locator that decouples modules from each other. Instead of passing concrete references between modules directly, each module publishes its services to the registry and other modules resolve them by type.

## Service marker interface

Any class you want to register must implement `Service`:

```java
public interface Service {}
```

This is a marker interface — no methods required. It simply makes the registry's type parameter explicit.

## Reloadable

Services that support hot-reload implement `Reloadable`:

```java
public interface Reloadable {
    void reload() throws Exception;
}
```

`ConfigManager` and `MessageService` implement both `Service` and `Reloadable`.

## Publishing a service

From inside a module that extends `AbstractModule`:

```java
@Override
public void onEnable() {
    MyService service = new MyService(plugin);
    provide(MyService.class, service);   // registers under MyService.class key
}
```

Directly via the registry:

```java
plugin.services().register(MyService.class, new MyService(plugin));
```

Registering the same type twice throws `IllegalStateException` — one service per type, always.

## Resolving a service

From inside a module:

```java
MyService service = service(MyService.class);      // throws if not registered
Optional<MyService> opt = services().find(MyService.class);   // safe
```

From anywhere with a plugin reference:

```java
MyService service = plugin.services().get(MyService.class);
```

## Lifecycle

The registry is cleared (`services.clear()`) during `CorePlugin.onDisable()`, after all modules have been disabled. Services should not be resolved after `onDisable()` is called.

## Example: FoundationModule

The reference `FoundationModule` shows how core services are published so all later modules can consume them:

```java
@Override
public void onEnable() {
    ConfigManager configs = new ConfigManager(plugin);
    provide(ConfigManager.class, configs);

    MessageService messages = new MessageService(plugin);
    provide(MessageService.class, messages);

    SchedulerService scheduler = new SchedulerService(plugin);
    provide(SchedulerService.class, scheduler);

    ListenerRegistry listeners = new ListenerRegistry(plugin);
    provide(ListenerRegistry.class, listeners);
}
```

A `CommandModule` that starts after `FoundationModule` can then do:

```java
MessageService messages = service(MessageService.class);
CommandRegistry commands = new CommandRegistry(plugin, messages);
provide(CommandRegistry.class, commands);
```
