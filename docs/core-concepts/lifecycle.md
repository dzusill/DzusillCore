# Plugin Lifecycle

## CorePlugin

`CorePlugin` is the abstract base every plugin built on this framework extends. It owns the `ServiceRegistry` and drives the `ModuleManager`. The `onEnable()` and `onDisable()` methods are `final` so the framework always controls the startup/shutdown sequence.

```
onEnable()
  └─ printBanner()
  └─ onPreEnable()          ← override for work before modules start
  └─ ModuleManager.enableAll(modules())
       ├─ module[0].onEnable()
       ├─ module[1].onEnable()
       └─ ...
  └─ onPostEnable()         ← override for work after all modules are up

onDisable()
  └─ onPreDisable()         ← override for work before modules stop
  └─ ModuleManager.disableAll()
       ├─ module[n].onDisable()   ← reverse order
       └─ ...
  └─ ServiceRegistry.clear()
```

## Lifecycle hooks

Override these in your plugin class for pre/post module work:

```java
public class MyPlugin extends CorePlugin {

    @Override
    protected void onPreEnable() {
        // runs before any module is started
    }

    @Override
    protected void onPostEnable() {
        // runs after all modules are running
    }

    @Override
    protected void onPreDisable() {
        // runs before any module is stopped
    }
}
```

## Singleton access

`CorePlugin` maintains a static `instance()` method. Use it only when a direct reference cannot be passed (e.g. from a static context):

```java
CorePlugin plugin = CorePlugin.instance();
// or cast to your subclass:
MyPlugin plugin = (MyPlugin) CorePlugin.instance();
```

`instance()` returns `null` after the plugin has been disabled.

## Service registry access

The `ServiceRegistry` is shared across all modules and is accessible from the plugin:

```java
MessageService messages = plugin.services().get(MessageService.class);
```

Inside a module that extends `AbstractModule`, use the shorthand:

```java
MessageService messages = service(MessageService.class);
```

## The banner

Override `banner()` to customize what appears in the console on startup:

```java
@Override
protected String[] banner() {
    return new String[]{
            "",
            "  MyPlugin v" + getPluginMeta().getVersion(),
            "  Author: yourname",
            ""
    };
}
```
